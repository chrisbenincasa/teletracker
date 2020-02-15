import Head from 'next/head';
import React from 'react';
import AppWrapper from '../../containers/AppWrapper';
import Login from '../../containers/Login';

interface Props {
  pageProps: any;
}

function LoginWrapper(props: Props) {
  return (
    <React.Fragment>
      <Head>
        <title>Login</title>
        <meta
          name="viewport"
          content="minimum-scale=1, initial-scale=1, width=device-width"
        />
      </Head>
      <AppWrapper>
        <Login />
      </AppWrapper>
    </React.Fragment>
  );
}

export default LoginWrapper;
