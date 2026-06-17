from django.apps.registry import Apps
from django.db import migrations
from django.db.models import Max



def create_built_in_roles(apps: Apps, schema_editor):
    RoleCategory = apps.get_model('web', 'RoleCategory')
    Role = apps.get_model('web', 'Role')

    last_index = Role.objects.aggregate(max_index=Max('index'))['max_index']

    category = RoleCategory.objects.create(
        name='Статус пользователя'
    )

    reader_role = Role.objects.create(
        slug='reader',
        name='Читатель',
        category=category,
        votes_title='Голоса читателей',
        profile_visual_mode='status',
        group_votes=True,
        index=last_index+2,
    )
    editor_role = Role.objects.create(
        slug='editor',
        name='Участник',
        category=category,
        votes_title='Голоса участников',
        profile_visual_mode='status',
        group_votes=True,
        index=last_index+1
    )

    registered_role = Role.objects.get(slug='registered')
    everyone_role = Role.objects.get(slug='everyone')

    registered_role.index = last_index+3
    everyone_role.index = last_index+4

    registered_role.save()
    everyone_role.save()


class Migration(migrations.Migration):

    dependencies = [
        ('auth', '0012_alter_user_first_name_max_length'),
        ('web', '0002_create_default_roles'),
    ]

    operations = [
        # Create roles for readers and editors
        migrations.RunPython(create_built_in_roles, migrations.RunPython.noop, atomic=True),
    ]
