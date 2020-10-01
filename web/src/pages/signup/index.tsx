import Head from 'next/head';
import React from 'react';
import Signup from '../../containers/Signup';

export default function SignupWrapper() {
  return (
    <React.Fragment>
      <Head>
        <title>Signup</title>
      </Head>
      <Signup />
    </React.Fragment>
  );
}
