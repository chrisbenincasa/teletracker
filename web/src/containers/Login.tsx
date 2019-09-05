import {
  Avatar,
  Button,
  CircularProgress,
  createStyles,
  FormControl,
  Input,
  InputLabel,
  Link,
  Paper,
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
import { login, LoginSuccessful, logInWithGoogle } from '../actions/auth';
import { AppState } from '../reducers';
import { Redirect } from 'react-router';
import { Link as RouterLink } from 'react-router-dom';
import * as firebase from 'firebase/app';
import GoogleLoginButton from '../components/GoogleLoginButton';

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
      position: 'relative',
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
      backgroundColor: 'rgba(255, 255, 255, 0.8)',
      zIndex: 1000,
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

interface Props extends WithStyles<typeof styles> {
  isAuthed: boolean;
  login: (email: string, password: string) => void;
  logInWithGoogle: () => any;
  isLoggingIn: boolean;
  logInSuccessful: (token: string) => any;
  changePage: () => void;
}

interface State {
  email: string;
  password: string;
}

class Login extends Component<Props, State> {
  state: State = {
    email: '',
    password: '',
  };

  componentDidMount(): void {
    firebase
      .auth()
      .getRedirectResult()
      .then(result => {
        if (result.user) {
          result.user.getIdToken().then(token => {
            this.props.logInSuccessful(token);
          });
        }
      })

      .catch(console.error);
  }

  logInWithGoogle = () => {
    this.props.logInWithGoogle();
  };

  onSubmit(ev: FormEvent<HTMLFormElement>) {
    ev.preventDefault();

    this.props.login(this.state.email, this.state.password);

    this.setState({
      email: '',
      password: '',
    });

    // TODO: Protect this with some state.
    push('/');
  }

  render() {
    let { isAuthed, classes } = this.props;
    let { email, password } = this.state;

    return !isAuthed ? (
      <main className={classes.main}>
        <Paper className={classes.paper}>
          {this.props.isLoggingIn ? (
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
            <form className={classes.form} onSubmit={ev => this.onSubmit(ev)}>
              <FormControl margin="normal" required fullWidth>
                <InputLabel htmlFor="email">Email</InputLabel>
                <Input
                  id="email"
                  name="email"
                  autoComplete="email"
                  autoFocus={!this.props.isLoggingIn}
                  type="email"
                  onChange={e => this.setState({ email: e.target.value })}
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
                <Link component={RouterLink} to="/signup">
                  Sign up now!
                </Link>
              </Typography>
            </form>
          </div>
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
    isLoggingIn: appState.auth.isLoggingIn,
  };
};

const mapDispatchToProps = dispatch =>
  bindActionCreators(
    {
      login: (email: string, password: string) => login(email, password),
      changePage: () => push('/'),
      logInWithGoogle,
      logInSuccessful: (token: string) => LoginSuccessful(token),
    },
    dispatch,
  );

export default withStyles(styles)(
  connect(
    mapStateToProps,
    mapDispatchToProps,
  )(Login),
);
