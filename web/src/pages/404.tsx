import {
  createStyles,
  Theme,
  Typography,
  withStyles,
  WithStyles,
} from '@material-ui/core';
import React, { Component } from 'react';
import Head from 'next/head';

const styles = (theme: Theme) =>
  createStyles({
    wrapper: {
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      height: '70vh',
      width: '100%',
    },
  });

interface OwnProps extends WithStyles<typeof styles> {}

type Props = OwnProps;

class NoMatch404 extends Component<Props> {
  componentDidMount(): void {
    // TODO: GA
    // const page = window.location.pathname + window.location.search;
    // ReactGA.pageview(page);
    // ReactGA.event({
    //   category: '404',
    //   action: page,
    //   value: 1,
    //   nonInteraction: true,
    // });
  }

  render() {
    const { classes } = this.props;

    return (
      <React.Fragment>
        <Head>
          <title>{`404 | Telescope`}</title>
        </Head>
        <div className={classes.wrapper}>
          <Typography variant="h1">404</Typography>
        </div>
      </React.Fragment>
    );
  }
}

export default withStyles(styles)(NoMatch404);
