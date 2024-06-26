import Head from 'next/head';
import React from 'react';
import Login from '../../containers/Login';

interface Props {
  pageProps: any;
}

export default function LoginWrapper(props: Props) {
  return (
    <React.Fragment>
      <Head>
        <title>Login</title>
      </Head>
      <Login />
    </React.Fragment>
  );
}
