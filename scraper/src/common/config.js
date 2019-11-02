import AWS from 'aws-sdk';

AWS.config = {
  region: process.env.REGION || 'us-west-1',
};

export {};
