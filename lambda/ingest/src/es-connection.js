import AWS from 'aws-sdk';
import { Connection } from '@elastic/elasticsearch';
import aws4 from 'aws4';

export default class AwsConnection extends Connection {
  buildRequestObject(params) {
    let prebuilt = super.buildRequestObject(params);

    let optsWithAws = {
      ...prebuilt,
      host: process.env.ES_HOST,
      region: process.env.AWS_REGION,
    };

    aws4.sign(optsWithAws);

    return optsWithAws;
  }
}
