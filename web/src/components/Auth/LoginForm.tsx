import {
  Avatar,
  Button,
  CircularProgress,
  createStyles,
  FormControl,
  Input,
  InputLabel,
  Link,
  Theme,
  Typography,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import LockOutlinedIcon from '@material-ui/icons/LockOutlined';
import { push } from 'connected-react-router';
import * as R from 'ramda';
import React, { Component, FormEvent } from 'react';
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';
import { login, LoginSuccessful, logInWithGoogle } from '../../actions/auth';
import { AppState } from '../../reducers';
import GoogleLoginButton from './GoogleLoginButton';
import ReactGA from 'react-ga';
import { GOOGLE_ACCOUNT_MERGE } from '../../constants/';
import { WithRouterProps } from 'next/dist/client/with-router';
import { withRouter } from 'next/router';
import qs from 'querystring';

const styles = (theme: Theme) =>
  createStyles({
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
    overlay: {
      display: 'flex',
      flexDirection: 'column',
      justifyContent: 'center',
      alignItems: 'center',
      position: 'absolute',
      top: 0,
      left: 0,
      width: '100%',
      height: '100%',
      backgroundColor: theme.palette.action.active,
      zIndex: theme.zIndex.modal,
    },
    socialSignInContainer: {
      marginTop: theme.spacing(1),
    },
    progressSpinner: {
      marginBottom: theme.spacing(1),
    },
    googleButtonIcon: {
      height: 30,
    },
    googleButtonText: {
      marginLeft: theme.spacing(1),
    },
    signUpLinkText: {
      marginTop: theme.spacing(2),
      textAlign: 'center',
    },
  });

interface OwnProps {
  isAuthed: boolean;
  login: (email: string, password: string) => void;
  logInWithGoogle: () => any;
  isLoggingIn: boolean;
  logInSuccessful: (token: string) => any;
  redirect_uri?: string;
  // Events
  onSubmitted?: () => void;
  onNav?: () => void;
  onLogin?: () => void;
}

type Props = OwnProps & WithStyles<typeof styles> & WithRouterProps;

interface State {
  email: string;
  password: string;
  cameFromOAuth: boolean;
  mergedFederatedAccount: boolean;
}

class LoginForm extends Component<Props, State> {
  constructor(props: Readonly<Props>) {
    super(props);
    const params = new URLSearchParams(qs.stringify(props.router.query));

    // If a user already has an account with email X and then attempts to sign
    // in with a federatedk identity (e.g. Google) our pre-signup lambda will
    // intercept and throw an error to indicate that it merged the federated
    // identity with the cognito user. This is returned in the form of an error
    // in the URL and indicates we should retry the federated login (because
    // with Cognito, the first attempt at a federated login with a matching
    // email causes an error)
    let error = params.get('error_description');
    console.log(params.get('code'));

    this.state = {
      email: '',
      password: '',
      cameFromOAuth: params.get('code') !== null,
      mergedFederatedAccount: error
        ? error.includes(GOOGLE_ACCOUNT_MERGE)
        : false,
    };
  }

  componentDidMount(): void {
    if (!this.state.cameFromOAuth) {
      ReactGA.pageview(window.location.pathname + window.location.search);
    }

    if (this.state.mergedFederatedAccount) {
      this.logInWithGoogle();
    }
  }

  componentDidUpdate(prevProps: Readonly<Props>): void {
    if (!this.props.isLoggingIn && prevProps.isLoggingIn) {
      if (this.props.onLogin) {
        this.props.onLogin();
      }
    }
  }

  logInWithGoogle = () => {
    this.props.logInWithGoogle();
  };

  onSubmit = (ev: FormEvent<HTMLFormElement>) => {
    ev.preventDefault();

    if (this.props.onSubmitted) {
      this.props.onSubmitted();
    }

    this.props.login(this.state.email, this.state.password);

    // TODO: Protect this with some state.
    this.props.router.push('/');
  };

  render() {
    let { classes, isLoggingIn } = this.props;
    let { email, password } = this.state;

    return (
      <React.Fragment>
        {this.props.isLoggingIn ||
        this.state.cameFromOAuth ||
        this.state.mergedFederatedAccount ? (
          <div className={classes.overlay}>
            <CircularProgress className={classes.progressSpinner} />
            <div>
              <Typography> Logging in&hellip;</Typography>
            </div>
          </div>
        ) : null}
        <Avatar className={classes.avatar}>
          <LockOutlinedIcon />
        </Avatar>
        <Typography component="h1" variant="h5">
          Log in
        </Typography>

        <div className={classes.socialSignInContainer}>
          <GoogleLoginButton onClick={this.logInWithGoogle} />
        </div>
        <div>
          <form className={classes.form} onSubmit={this.onSubmit}>
            <FormControl margin="normal" required fullWidth>
              <InputLabel htmlFor="email">Email</InputLabel>
              <Input
                id="email"
                name="email"
                autoComplete="email"
                autoFocus={!this.props.isLoggingIn}
                type="email"
                onChange={e => this.setState({ email: e.target.value })}
                disabled={isLoggingIn}
                value={email}
              />
            </FormControl>
            <FormControl margin="normal" required fullWidth>
              <InputLabel htmlFor="password">Password</InputLabel>
              <Input
                id="password"
                name="password"
                autoComplete="password"
                type="password"
                onChange={e => this.setState({ password: e.target.value })}
                disabled={isLoggingIn}
                value={password}
              />
            </FormControl>

            <Button
              type="submit"
              fullWidth
              variant="contained"
              color="primary"
              className={classes.submit}
            >
              Log in
            </Button>
            <Typography className={classes.signUpLinkText}>
              Don't have an account?&nbsp;
              {this.props.onNav ? (
                <Link onClick={this.props.onNav}>Signup!</Link>
              ) : (
                <Link href="/signup">Signup!</Link>
              )}
            </Typography>
          </form>
        </div>
      </React.Fragment>
    );
  }
}

const mapStateToProps = (appState: AppState) => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
    isLoggingIn: appState.auth.isLoggingIn,
  };
};

const mapDispatchToProps = dispatch =>
  bindActionCreators(
    {
      login: (email: string, password: string) => login(email, password),
      logInWithGoogle,
      logInSuccessful: (token: string) => LoginSuccessful(token),
    },
    dispatch,
  );

export default withRouter(
  withStyles(styles)(connect(mapStateToProps, mapDispatchToProps)(LoginForm)),
);
