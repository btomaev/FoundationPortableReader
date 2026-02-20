import os
import queue
import sys
import threading
import django
import concurrent.futures
import requests

from io import BytesIO
from urllib.parse import urlencode, quote
from requests.adapters import HTTPAdapter

from django.core.wsgi import get_wsgi_application
from django.http.request import HttpHeaders, HttpRequest
from django.db import transaction

from web import threadvars
from web.util import json


json.replace_json_dumps_default()

class JavaInterface:
    FETCHER_WIKI_BASE = 'https://scpfoundation.net'
    FETCHER_MAX_CONNECTIONS = 200
    FETCHER_TIMEOUT = 5

    def __init__(self):
        self.ready = False

    def check_and_setup(self, root_uri: str=None):
        os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'scpdev.settings')
        if root_uri:
            os.environ['BASE_DIR'] = root_uri

        django.setup()

        from django.core.management import execute_from_command_line
        execute_from_command_line(['manage.py', 'migrate'])
        execute_from_command_line(['manage.py', 'collectstatic', '--no-input'])

        self.init_create_site_if_not_exists()     
        self.application = self.get_application()

        self.ready = True

    def dispose(self):
        del self.application
        del self.site
        del self

    def is_ready(self):
        return self.ready

    def get_application(slef):
        from web import permissions
        permissions.register_role_permissions()

        return get_wsgi_application()
    
    def init_create_site_if_not_exists(self):
        from web.models import Site, Settings
        if Site.objects.exists():
            self.site = Site.objects.get()
            return

        site = Site(
            title='SCP Foundation',
            headline='Читалковая ветка',
            slug='scpru',
            domain='localhost:8000',
            media_domain='localhost:8000'
        )
        site.save()

        settings = Settings(site=site)
        settings.save()

        self.site = site

        # self.import_pages([
        #     'main', 'nav:top', 'nav:top-impl', 'nav:side', 'nav:side-impl', 'fragment:main-banner',
        #     'news', 'most-recently-created', 'system:recent-changes', 'forum:recent-posts',
        #     'system:join', 'sandbox:main', 'contacts', 'faq',
        #     'system:where-to-start', 'wiki-syntax-guide'
        # ])

    def get_page(self, path: str, method: str='GET', java_headers: dict[str, str]=None, data: dict=None):
        body = b''
        content_type = ''

        if java_headers:
            headers = {key: java_headers.get(key) for key in java_headers.keySet().toArray()}
        else:
            headers = {}
        if method.upper() == 'POST' and data:
            body = urlencode(data).encode('utf-8')
            content_type = 'application/x-www-form-urlencoded'

        environ = {
            'REQUEST_METHOD': method.upper(),
            'PATH_INFO': quote(path, safe='/:'),
            'SERVER_NAME': 'localhost',
            'SERVER_PORT': '8000',
            'HTTP_HOST': 'localhost',
            'REMOTE_ADDR': '127.0.0.1',
            'GATEWAY_INTERFACE': 'CGI/1.1',
            'wsgi.version': (1, 0),
            'wsgi.url_scheme': 'http',
            'wsgi.input': BytesIO(body),
            'wsgi.errors': sys.stderr,
            'wsgi.multithread': True,
            'wsgi.multiprocess': False,
            'wsgi.run_once': False,
            'CONTENT_LENGTH': str(len(body)),
            'CONTENT_TYPE': content_type,
        }

        environ.update(HttpHeaders.to_wsgi_names(headers))

        response_headers = []
        status_code = 0
        reason = ''

        def start_response(status, headers, exc_info=None):
            nonlocal response_headers, status_code, reason
            status_code, reason = status.split(' ', 1)
            response_headers = headers

        result = self.application(environ, start_response)

        return int(status_code), reason, response_headers, b''.join(result)
    
    def import_pages(self, pages: list[str], progress_callback=None):
        max_connections = JavaInterface.FETCHER_MAX_CONNECTIONS
        timeout = JavaInterface.FETCHER_TIMEOUT
        wiki_base = JavaInterface.FETCHER_WIKI_BASE

        total_pages = len(pages)
        total_fetched = 0
        imported = []
        failed = []

        thread_local = threading.local()
        import_queue = queue.Queue()
        sentinel = object()

        from web.controllers import articles
        from web.models import User, Article
        from web.views.api.files import GetOrUploadView

        def get_session():
            if not hasattr(thread_local, "session"):
                session = requests.Session()
                adapter = HTTPAdapter(pool_maxsize=50)
                session.mount('https://', adapter)
                session.mount('http://', adapter)
                thread_local.session = session
            return thread_local.session
        
        def import_users(users: list[dict[str, object]]):
            return [
                User.objects.get_or_create(
                    username=user['name'],
                    defaults = {
                        "wikidot_username": user['username'],
                        "type": user['type']
                    }
                )[0] for user in users
            ]
        
        def import_files(article: Article, files: list[dict[str, object]]):
            session = get_session()
            for file in files:
                file_name = str(file['name'])
                with session.get(wiki_base+'/local--files/'+article.full_name+'/'+file_name, timeout=timeout) as resp:
                    # print(resp.status_code, resp.headers.get('content-type'), file_name)
                    GetOrUploadView._upload_file(article, file_name, BytesIO(resp.content), content_type=resp.headers.get('content-type'))


        def page_importer():
            nonlocal total_fetched, imported, failed, total_pages
            while True:
                data = import_queue.get()
                if data is sentinel:
                    import_queue.task_done()
                    break
                
                with threadvars.context():
                    threadvars.put('current_site', self.site)

                    if progress_callback:
                        progress_callback.invoke(data['pageId'], total_fetched, len(imported), len(failed), total_pages)
                    
                    with transaction.atomic():
                        a = articles.get_article(data['pageId'])
                        if not a:
                            authors = import_users(data['authors'])
                            a = articles.create_article(data['pageId'], authors[0])
                        
                            articles.create_article_version(a, data['source'])
                            articles.set_tags(a, data['tags'])
                            a.title = data['title']
                            a.authors.set(authors)

                            import_files(a, data['files'])

                            a.save()

                        imported.append(data['pageId'])

                    if progress_callback and progress_callback.invoke(data['pageId'], total_fetched, len(imported), len(failed), total_pages):
                        import_queue.task_done()
                        break
                
                import_queue.task_done()

        importer_thread = threading.Thread(target=page_importer)
        importer_thread.start()
        
        def load_page(executor: concurrent.futures.ThreadPoolExecutor, session: requests.Session, wiki_base: str, page_id: str, timeout: int, progress_callback=None):
            nonlocal total_fetched, imported, failed, total_pages
            session = get_session()
            is_ok = True
            data = {}

            try:
                with session.get(wiki_base+'/api/articles/'+page_id, timeout=timeout) as resp:
                    data = resp.json()
                with session.get(wiki_base+'/api/articles/'+page_id+'/files', timeout=timeout) as resp:
                    data['files'] = resp.json()['files']
            except:
                is_ok = False

            if is_ok and 'error' not in data:
                import_queue.put(data)
            else:
                is_ok = False

            if progress_callback and progress_callback.invoke(None, total_fetched, len(imported), len(failed)+int(not is_ok), total_pages):
                executor.shutdown(cancel_futures=True)
            
            return is_ok, page_id
        
        with concurrent.futures.ThreadPoolExecutor(max_workers=max_connections) as executor:
            session = get_session()
            future_to_url = (executor.submit(load_page, executor, session, wiki_base, page, timeout, progress_callback) for page in pages)

            for future in concurrent.futures.as_completed(future_to_url):
                result = future.result()
                if result[0]:
                    total_fetched += 1
                else:
                    failed.append(result[1])

        import_queue.put(sentinel)
        importer_thread.join()

        return imported, failed
