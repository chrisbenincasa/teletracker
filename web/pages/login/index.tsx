import Head from 'next/head';
import React from 'react';
import AppWrapper from '../../src/containers/AppWrapper';
import Login from '../../src/containers/Login';
import { Item } from '../../src/types/v2/Item';

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
