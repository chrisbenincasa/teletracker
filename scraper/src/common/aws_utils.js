import AWS from 'aws-sdk';

const getSsm = (() => {
  let ssm;
  return () => {
    if (!ssm) {
      console.log('Creating SSM client in region ' + process.env.AWS_REGION);
      ssm = new AWS.SSM({ region: process.env.AWS_REGION });
    }

    return ssm;
  };
})();

export const resolveSecret = async secret => {
  console.log('Fetching SSM from ' + process.env.AWS_REGION);
  return getSsm()
    .getParameter({
      Name: secret,
      WithDecryption: true,
    })
    .promise()
    .then(param => param.Parameter.Value);
};
