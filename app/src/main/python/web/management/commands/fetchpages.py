import requests

from django.core.management.base import BaseCommand

from web.controllers import articles


class Command(BaseCommand):
    help = 'Creates a website'

    def add_arguments(self, parser):
        parser.add_argument('-w', '--wiki', required=False, default='https://scpfoundation.net', help='Target wiki url')

    def handle(self, *args, **options):
        self.wiki_base = options['wiki']
        self.session = requests.session()
        while True:
            try:
                full_name = input('>> ')
            except:
                break
            if not full_name:
                continue
            data = self.fetch_article(full_name, )

            if not data:
                print(f'Article {full_name} not found')
                continue

            a = articles.get_article(full_name)
            if not a:
                a = articles.create_article(full_name)
            articles.create_article_version(a, data['source'])

            a = articles.get_article(full_name)

            a.title = data['title']
            a.save()

            print(f'Successful updated {full_name}')

    def fetch_article(self, full_name: str):
        resp = self.session.get(self.wiki_base + '/api/articles/'+full_name)
        if resp.status_code != 200:
            return None
        return resp.json()
