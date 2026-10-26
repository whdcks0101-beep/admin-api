#!/usr/bin/env python3
"""Provision a deliberately PUBLIC demo account; never use this password for real users."""
import pathlib
import runpy
import urllib.parse
import bcrypt

ROOT = pathlib.Path(__file__).resolve().parent
helpers = runpy.run_path(str(ROOT / 'link-admin-sso.py'), run_name='helpers')
mysql, sql_text, request = helpers['mysql'], helpers['sql_text'], helpers['request']
EMAIL = 'demo-admin@example.com'
PASSWORD = 'PortfolioDemo2026!'
ROLE = '체험관리자'


def main():
    env = dict(line.split('=', 1) for line in (ROOT / '.env').read_text().splitlines()
               if line and not line.startswith('#'))
    issuer = 'https://' + env['PUBLIC_HOST'] + '/sso/realms/portfolio'
    role = mysql('SELECT role FROM users WHERE email=' + sql_text(EMAIL) + ';')
    if role and role != ROLE:
        raise RuntimeError('Reserved demo email belongs to another role. No changes made.')
    if not role:
        hashed = '{bcrypt}' + bcrypt.hashpw(PASSWORD.encode(), bcrypt.gensalt()).decode()
        mysql('INSERT INTO users (name,email,role,password_hash) VALUES ('
              + ','.join(map(sql_text, ['공개 체험 관리자', EMAIL, ROLE, hashed])) + ');')
    user_id = mysql('SELECT id FROM users WHERE email=' + sql_text(EMAIL) + ';')
    if not user_id.isdigit():
        raise RuntimeError('Cannot resolve demo user.')
    auth, _ = request('/realms/master/protocol/openid-connect/token', 'POST', {
        'grant_type':'password','client_id':'admin-cli','username':'kc-admin',
        'password':env['KC_BOOTSTRAP_ADMIN_PASSWORD']}, form=True)
    token = auth['access_token']
    accounts, _ = request('/admin/realms/portfolio/users?' + urllib.parse.urlencode(
        {'username':EMAIL,'exact':'true'}), token=token)
    if accounts:
        subject = accounts[0]['id']
        linked = mysql('SELECT user_id FROM user_external_identities WHERE issuer='
                       + sql_text(issuer) + ' AND subject=' + sql_text(subject) + ';')
        if linked != user_id:
            raise RuntimeError('Existing Keycloak identity is not linked to this demo user. Review manually.')
    else:
        _, headers = request('/admin/realms/portfolio/users','POST',{
            'username':EMAIL,'email':EMAIL,'firstName':'Demo','lastName':'Admin',
            'enabled':False,'emailVerified':False,
            'credentials':[{'type':'password','value':PASSWORD,'temporary':False}]},token=token)
        subject=headers['Location'].rstrip('/').rsplit('/',1)[-1]
        mysql('INSERT INTO user_external_identities (user_id,issuer,subject) VALUES ('
              + user_id + ',' + sql_text(issuer) + ',' + sql_text(subject) + ');')
    request('/admin/realms/portfolio/users/' + urllib.parse.quote(subject,safe=''),
            'PUT',{'enabled':True},token=token)
    print('Public demo account ready; existing operating accounts were not modified.')


if __name__ == '__main__':
    main()
