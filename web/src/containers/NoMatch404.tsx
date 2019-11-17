import {
  createStyles,
  Theme,
  Typography,
  withStyles,
  WithStyles,
} from '@material-ui/core';
import React, { Component } from 'react';
import ReactGA from 'react-ga';
import { GA_TRACKING_ID } from '../constants';
import { Helmet } from 'react-helmet';

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
    ReactGA.initialize(GA_TRACKING_ID);
    const page = window.location.pathname + window.location.search;
    ReactGA.pageview(page);
    ReactGA.event({
      category: '404',
      action: page,
      value: 1,
      nonInteraction: true,
    });
  }

  render() {
    const { classes } = this.props;

    return (
      <React.Fragment>
        <Helmet>
          <title>{`404 | Teletracker`}</title>
        </Helmet>
        <div className={classes.wrapper}>
          <Typography variant="h1">404</Typography>
        </div>
      </React.Fragment>
    );
  }
}

export default withStyles(styles)(NoMatch404);
