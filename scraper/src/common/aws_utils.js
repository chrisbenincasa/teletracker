import AWS from 'aws-sdk';

const getSsm = (() => {
  let ssm;
  return () => {
    if (!ssm) {
      ssm = new AWS.SSM();
    }

    return ssm;
  };
})();

export const resolveSecret = async secret => {
  return getSsm()
    .getParameter({
      Name: secret,
      WithDecryption: true,
    })
    .promise()
    .then(param => param.Parameter.Value);
};
