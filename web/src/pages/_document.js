import React from 'react';
import NextDocument from 'next/document';
import { ServerStyleSheets } from '@material-ui/core/styles';

export default class Document extends NextDocument {
  static async getInitialProps(ctx) {
    const sheet = new ServerStyleSheets();
    const originalRenderPage = ctx.renderPage;

    ctx.renderPage = () =>
      originalRenderPage({
        enhanceApp: App => props => sheet.collect(<App {...props} />),
      });

    const initialProps = await NextDocument.getInitialProps(ctx);
    return {
      ...initialProps,
      styles: (
        <>
          {initialProps.styles}
          {sheet.getStyleElement()}
        </>
      ),
    };
    // try {
    // } finally {
    //   sheet.seal();
    // }
  }
}
