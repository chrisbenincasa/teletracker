import AWS from 'aws-sdk';

AWS.config.region = process.env.REGION || 'us-west-2';

export {};
