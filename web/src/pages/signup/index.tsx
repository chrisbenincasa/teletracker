import Head from 'next/head';
import React from 'react';
import AppWrapper from '../../containers/AppWrapper';
import Signup from '../../containers/Signup';

export default function SignupWrapper() {
  return (
    <React.Fragment>
      <Head>
        <title>Signup</title>
      </Head>
      <AppWrapper>
        <Signup />
      </AppWrapper>
    </React.Fragment>
  );
}
