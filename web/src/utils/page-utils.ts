import Auth, { CognitoUser } from '@aws-amplify/auth';

export const isServer = () => typeof window === 'undefined';

export async function currentUser(bypassCache: boolean = false) {
  let user: CognitoUser | undefined;
  try {
    user = await Auth.currentAuthenticatedUser({ bypassCache });
  } catch (e) {}

  return user;
}

export async function currentUserJwt(bypassCache: boolean = false) {
  const user = await currentUser(bypassCache);
  return extractUserJwt(user);
}

export function extractUserJwt(user?: CognitoUser) {
  if (user && user.getSignInUserSession()) {
    return user
      .getSignInUserSession()!
      .getAccessToken()
      .getJwtToken();
  }
}
