import Head from 'next/head';
import React from 'react';
import AboutUs from '../../containers/AboutUs';

export default function AboutUsWrapper() {
  return (
    <React.Fragment>
      <Head>
        <title>About Us</title>
      </Head>
      <AboutUs />
    </React.Fragment>
  );
}
