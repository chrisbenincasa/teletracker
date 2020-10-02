import Head from 'next/head';
import React from 'react';
import PrivacyPolicy from '../../containers/PrivacyPolicy';

export default function PrivacyPolicyWrapper() {
  return (
    <React.Fragment>
      <Head>
        <title>Privacy Policy</title>
      </Head>
      <PrivacyPolicy />
    </React.Fragment>
  );
}
