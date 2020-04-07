import { SSM } from '@aws-sdk/client-ssm';

export const getRegion = () => {
  return process.env.AWS_REGION || 'us-west-2';
};

const getSsm = (() => {
  let ssm: SSM;
  return () => {
    if (!ssm) {
      let region = getRegion();
      console.log('Creating SSM client in region ' + region);
      ssm = new SSM({ region });
    }

    return ssm;
  };
})();

export const resolveSecret = async (secret: string) => {
  console.log('Fetching SSM from ' + getRegion());
  return getSsm()
    .getParameter({
      Name: secret,
      WithDecryption: true,
    })
    .then((param) => param?.Parameter?.Value);
};
