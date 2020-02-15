import cookie from 'cookie';

export function extractApiKeyFromCookie(
  cookieString?: string,
): string | undefined {
  let parsed;
  if (cookieString) {
    parsed = cookie.parse(cookieString);
  }

  let key = Object.keys(parsed).find(
    key =>
      key.startsWith('CognitoIdentityServiceProvider') &&
      key.endsWith('accessToken'),
  );

  if (key && parsed) {
    return parsed[key];
  }
}
