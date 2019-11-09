import AWS from 'aws-sdk';

const signupHook = async event => {
  if (event.userName.startsWith('Google_')) {
    const cognitoClient = new AWS.CognitoIdentityServiceProvider({
      region: 'us-west-2',
    });

    const adminGetUserParams = {
      UserPoolId: 'us-west-2_K6E5m6v90',
      Username: event.request.userAttributes.email,
    };

    let success = false;
    try {
      let foundUser = await cognitoClient
        .adminGetUser(adminGetUserParams)
        .promise();

      const mergeParams = {
        DestinationUser: {
          ProviderAttributeValue: foundUser.Username,
          ProviderName: 'Cognito',
        },
        SourceUser: {
          ProviderAttributeName: 'Cognito_Subject',
          ProviderAttributeValue: event.userName.replace('Google_', ''),
          ProviderName: 'Google',
        },
        UserPoolId: 'us-west-2_K6E5m6v90',
      };

      await cognitoClient.adminLinkProviderForUser(mergeParams).promise();

      console.log('Successfully linked user to id ' + foundUser.Username);

      success = true;
    } catch (e) {
      console.error(e);
      return event;
    }

    if (success) {
      throw new Error('GOOGLE_ACCOUNT_MERGE');
    }
  } else {
    event.response.autoConfirmUser = true;
    return event;
  }
};

export default signupHook;
