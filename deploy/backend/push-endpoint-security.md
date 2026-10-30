# Browser push endpoint policy

Subscriptions and deliveries require HTTPS, no user information or URL fragment,
and either the default port or port 443. Endpoint length is limited to the
database column size. Subscription keys are checked for their Web Push lengths
before an existing subscription is changed.

Allowed origins are restricted to these browser push-service hostnames:

- `fcm.googleapis.com` and legacy `android.googleapis.com`
- Subdomains of `push.apple.com`
- Subdomains of `push.services.mozilla.com`
- Subdomains of `notify.windows.com`

HTTP redirects are disabled. Validation runs on incoming subscriptions, on stored
subscriptions before delivery, and again when constructing the HTTP request.
Unknown services are rejected; unsafe historical records are not contacted.
This avoids accepting arbitrary internal or attacker-owned endpoint origins.
DNS for the allowed vendor domains and the server's TLS trust store remain
trusted infrastructure. No arbitrary-domain override is provided.

When adding a browser service, verify its official endpoint domains and add
regression tests before changing the policy. Do not accept suffix matches
without a dot boundary or loosen the policy to any HTTPS URL.

References:

- Google example endpoint: https://web.dev/articles/codelab-notifications-push-server
- Apple allowed domain: https://developer.apple.com/documentation/usernotifications/sending-web-push-notifications-in-web-apps-and-browsers
- Microsoft channel URI validation: https://learn.microsoft.com/en-us/windows/apps/develop/notifications/push-notifications/wns-overview

Tests use generated keys and prepare requests without sending real push messages.
Real-device notification delivery should still be checked after deployment.
