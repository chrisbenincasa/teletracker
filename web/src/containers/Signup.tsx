import {
  createStyles,
  Paper,
  Theme,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import { push } from 'connected-react-router';
import * as R from 'ramda';
import React, { Component, FormEvent } from 'react';
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';
import { signup, signUpWithGoogle } from '../actions/auth';
import { AppState } from '../reducers';
import { Redirect } from 'react-router';
import ReactGA from 'react-ga';
import { GA_TRACKING_ID } from '../constants/';
import SignupForm from '../components/Auth/SignupForm';

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
      padding: `${theme.spacing(2)}px ${theme.spacing(3)}px ${theme.spacing(
        3,
      )}px`,
    },
    avatar: {
      margin: theme.spacing(1),
      backgroundColor: theme.palette.secondary.main,
    },
    form: {
      width: '100%', // Fix IE 11 issue.
      marginTop: theme.spacing(1),
    },
    submit: {
      marginTop: theme.spacing(3),
    },
    socialSignInContainer: {
      marginTop: theme.spacing(1),
    },
    signUpLinkText: {
      marginTop: theme.spacing(2),
      textAlign: 'center',
    },
  });

interface Props extends WithStyles<typeof styles> {
  isAuthed: boolean;
  signup: (username: string, email: string, password: string) => void;
  signUpWithGoogle: () => any;
  changePage: () => void;
}

interface State {
  username: string;
  email: string;
  password: string;
}

class Signup extends Component<Props, State> {
  state: State = {
    username: '',
    email: '',
    password: '',
  };

  componentDidMount(): void {
    ReactGA.initialize(GA_TRACKING_ID);
    ReactGA.pageview(window.location.pathname + window.location.search);
  }

  render() {
    let { isAuthed, classes } = this.props;

    return !isAuthed ? (
      <main className={classes.main}>
        <Paper className={classes.paper}>
          <SignupForm />
        </Paper>
      </main>
    ) : (
      <Redirect to="/" />
    );
  }
}

const mapStateToProps = (appState: AppState) => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
  };
};

const mapDispatchToProps = dispatch =>
  bindActionCreators(
    {
      signup: (username: string, email: string, password: string) =>
        signup(username, email, password),
      signUpWithGoogle,
      changePage: () => push('/'),
    },
    dispatch,
  );

export default withStyles(styles)(
  connect(
    mapStateToProps,
    mapDispatchToProps,
  )(Signup),
);
