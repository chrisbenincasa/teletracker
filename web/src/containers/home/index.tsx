import {
  createStyles,
  CssBaseline,
  Theme,
  Typography,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import * as R from 'ramda';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import { Redirect } from 'react-router-dom';
import { bindActionCreators } from 'redux';
import { AppState } from '../../reducers';

const styles = (theme: Theme) => createStyles({});

interface Props extends WithStyles<typeof styles> {
  isAuthed: boolean;
}

class Home extends Component<Props> {
  render() {
    return this.props.isAuthed ? (
      <main>
        <CssBaseline />
        <Typography component="h1" variant="h2">
          Welcome
        </Typography>
      </main>
    ) : (
      <Redirect to="/login" />
    );
  }
}

const mapStateToProps = (appState: AppState) => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
  };
};

const mapDispatchToProps = dispatch => bindActionCreators({}, dispatch);

export default withStyles(styles)(
  connect(
    mapStateToProps,
    mapDispatchToProps,
  )(Home),
);
