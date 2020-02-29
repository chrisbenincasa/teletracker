import {
  createStyles,
  Paper,
  Theme,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import * as R from 'ramda';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';
import { login, LoginSuccessful, logInWithGoogle } from '../actions/auth';
import { AppState } from '../reducers';
import ReactGA from 'react-ga';
import LoginForm from '../components/Auth/LoginForm';
import { withRouter, Router } from 'next/router';
import { WithRouterProps } from 'next/dist/client/with-router';

const styles = (theme: Theme) =>
  createStyles({
    main: {
      width: 'auto',
      display: 'block', // Fix IE 11 issue.
      marginLeft: theme.spacing(3),
      marginRight: theme.spacing(3),
      [theme.breakpoints.up(400 + theme.spacing(6))]: {
        width: 400,
        marginLeft: 'auto',
        marginRight: 'auto',
      },
    },
    paper: {
      marginTop: theme.spacing(8),
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      padding: theme.spacing(2, 3, 3),
      position: 'relative',
    },
  });

interface Props extends WithStyles<typeof styles> {
  isAuthed: boolean;
  isLoggingIn: boolean;
  redirect_uri?: string;
}

interface State {
  email: string;
  password: string;
}

class Login extends Component<Props & WithRouterProps, State> {
  state: State = {
    email: '',
    password: '',
  };

  componentDidMount(): void {
    if (this.props.isAuthed) {
      this.props.router.replace('/');
    }

    ReactGA.pageview(window.location.pathname + window.location.search);
  }

  handleLogin(): void {
    this.props.router.push('/');
  }

  render() {
    let { classes } = this.props;
    return (
      <div className={classes.main}>
        <Paper className={classes.paper}>
          <LoginForm onLogin={() => this.handleLogin()} />
        </Paper>
      </div>
    );
  }
}

const mapStateToProps = (appState: AppState) => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
    isLoggingIn: appState.auth.isLoggingIn,
  };
};

export default withRouter(withStyles(styles)(connect(mapStateToProps)(Login)));
