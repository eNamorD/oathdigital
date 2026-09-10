# Network, HTTPS proxy, and browser guidance

Seat links are bearer credentials for a trusted group. Anyone with a link can
control its seat. Oath Digital trusted-alpha mode does not add accounts,
passwords, remote administration, or protection against malicious players.

## Browser support status

Browser support remains provisional until each release is manually accepted.
Automated HTTP smoke tests do not establish browser compatibility.

| Browser family | Current status |
| --- | --- |
| Chromium-based desktop browsers | Untested for this build |
| Firefox desktop | Untested for this build |
| Safari desktop and mobile | Untested for this build |
| Mobile Chromium-based browsers | Untested for this build |

Record exact browser names, versions, operating systems, and results in the
[alpha acceptance record](alpha-acceptance.md). Do not convert this table to a
support claim without observed per-build evidence.

The server exchanges `/s/{seat-code}` for an `HttpOnly`, `SameSite=Lax` cookie
scoped to `/games/{game-id}`. An HTTPS public base URL also makes the cookie
`Secure`. These attributes and their browser behavior are described in MDN's
[Set-Cookie reference](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Set-Cookie).
Use separate browser profiles for two seats in the same game, because those
seats share one cookie name and game-scoped path.

## Private LAN hosting

Find the host's private address on the active LAN interface. Useful read-only
commands include:

```sh
# macOS Wi-Fi, when the interface is en0
ipconfig getifaddr en0

# Linux
ip -brief address
```

On Windows PowerShell:

```powershell
Get-NetIPAddress -AddressFamily IPv4
```

Choose the address reachable from player machines, for example
`192.168.1.20`, and use the exact browser-visible origin:

```sh
OATH_HOST=0.0.0.0 \
OATH_PORT=8080 \
OATH_PUBLIC_BASE_URL=http://192.168.1.20:8080 \
OATH_DATABASE_PATH=/home/alex/oathdigital-data/alpha-1/database \
bin/oathdigital
```

If a host firewall blocks the service, allow inbound TCP port 8080 only from
the private LAN subnet used by the players. Do not create an Internet-wide
rule, router port-forward, or automatic UPnP mapping. Firewall products and LAN
subnets differ, so verify the proposed rule's source range before applying it.
The [alpha acceptance record](alpha-acceptance.md) requires a second physical
machine; a second tab on the host is not LAN evidence.

Plain HTTP is suitable only for a private trusted LAN whose users and network
are trusted. Internet exposure requires HTTPS at a trusted reverse proxy.

## HTTPS reverse proxy

Terminate TLS at the proxy and bind the Oath Digital backend only to loopback.
The public base URL must be the exact external HTTPS origin; it controls seat
links, request-origin checks, and the cookie's `Secure` attribute:

```sh
OATH_HOST=127.0.0.1 \
OATH_PORT=8080 \
OATH_PUBLIC_BASE_URL=https://oath.example.com \
OATH_DATABASE_PATH=/home/alex/oathdigital-data/alpha-1/database \
bin/oathdigital
```

The following NGINX example belongs inside the `http` context. Replace the
certificate paths and host name with operator-controlled values:

```nginx
log_format oath_no_secrets '$remote_addr [$time_local] '
                           '"$request_method $uri $server_protocol" '
                           '$status $body_bytes_sent "$http_user_agent"';

server {
    listen 443 ssl;
    server_name oath.example.com;

    ssl_certificate     /etc/letsencrypt/live/oath.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/oath.example.com/privkey.pem;

    location ^~ /s/ {
        access_log off;
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-User "";
        proxy_set_header X-Remote-User "";
        proxy_set_header Remote-User "";
        proxy_set_header Authorization "";
    }

    location / {
        access_log /var/log/nginx/oathdigital-access.log oath_no_secrets;
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-User "";
        proxy_set_header X-Remote-User "";
        proxy_set_header Remote-User "";
        proxy_set_header Authorization "";
    }
}
```

NGINX documents that `access_log off` cancels access logging at its current
location in the official
[access-log module reference](https://nginx.org/en/docs/http/ngx_http_log_module.html).
Its official [proxy module reference](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_set_header)
documents that an empty `proxy_set_header` value omits that header upstream.

The credential-exchange `/s/` location disables access logging because its URI
contains the raw seat code. The remaining log format uses `$uri`, not
`$request`, and does not include `$args`, `$http_referer`, `$http_cookie`, or
any `$cookie_*` variable.
Audit load balancers, CDNs, web-application firewalls, error-reporting agents,
and hosting dashboards too: either disable request logging for `/s/` there or
apply tested redaction, and never record `Cookie` or `Set-Cookie` headers.

Do not expose port 8080 beyond loopback when using this proxy. Do not treat
`X-Forwarded-User`, `X-Remote-User`, `Remote-User`, `Authorization`, client IP,
or any other forwarded header as player identity. Oath Digital derives trusted
seat identity only from its seat cookie; the blank header settings above are
defense in depth, not an authentication integration.

Before inviting players, verify from a separate machine that:

- `https://oath.example.com/health/ready` succeeds;
- generated seat links start with exactly `https://oath.example.com/s/`;
- visiting a seat link redirects to the same HTTPS origin;
- the returned seat cookie has `Secure`, `HttpOnly`, `SameSite=Lax`, and the
  expected `/games/{game-id}` path; and
- direct access to the backend port from the LAN or Internet fails.
