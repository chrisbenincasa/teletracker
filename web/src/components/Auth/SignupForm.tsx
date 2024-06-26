import {
  Avatar,
  Button,
  CircularProgress,
  createStyles,
  Divider,
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
import * as R from 'ramda';
import React, { Component, FormEvent } from 'react';
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';
import { signupInitiated, signUpWithGoogle } from '../../actions/auth';
import { AppState } from '../../reducers';
import GoogleLoginButton from './GoogleLoginButton';
import { withRouter } from 'next/router';
import { WithRouterProps } from 'next/dist/client/with-router';
import NextLink from 'next/link';

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
      backgroundColor: 'rgba(0, 0, 0, 0.5)',
      zIndex: theme.zIndex.modal,
    },
    progressSpinner: {
      marginBottom: theme.spacing(1),
    },
    avatar: {
      margin: theme.spacing(1),
      backgroundColor: theme.palette.secondary.main,
    },
    form: {
      width: '100%', // Fix IE 11 issue.
    },
    submit: {
      marginTop: theme.spacing(3),
    },
    socialSignInContainer: {
      marginTop: theme.spacing(2),
    },
    signUpLinkText: {
      marginTop: theme.spacing(2),
      textAlign: 'center',
    },
  });

interface Props extends WithStyles<typeof styles>, WithRouterProps {
  isAuthed: boolean;
  isSigningUp: boolean;
  signup: (username: string, email: string, password: string) => void;
  signUpWithGoogle: () => any;
  onNav?: () => void;
}

interface State {
  username: string;
  email: string;
  password: string;
}

class SignupForm extends Component<Props, State> {
  state: State = {
    username: '',
    email: '',
    password: '',
  };

  componentDidUpdate(
    prevProps: Readonly<Props>,
    prevState: Readonly<State>,
    snapshot?: any,
  ): void {
    if (!this.props.isSigningUp && Boolean(prevProps.isSigningUp)) {
      this.props.router.push('/');
    }
  }

  signUpWithGoogle = () => {
    this.props.signUpWithGoogle();
  };

  onSubmit(ev: FormEvent<HTMLFormElement>) {
    ev.preventDefault();

    this.props.signup(
      this.state.username,
      this.state.email,
      this.state.password,
    );

    this.setState({
      username: '',
      email: '',
      password: '',
    });
  }

  render() {
    let { classes } = this.props;
    let { email, password } = this.state;

    return (
      <React.Fragment>
        {this.props.isSigningUp ? (
          <div className={classes.overlay}>
            <CircularProgress className={classes.progressSpinner} />
            <div>
              <Typography> Signing up&hellip;</Typography>
            </div>
          </div>
        ) : null}
        <Avatar className={classes.avatar}>
          <LockOutlinedIcon />
        </Avatar>
        <Typography component="h1" variant="h5">
          Sign Up
        </Typography>

        <Divider />

        <div className={classes.socialSignInContainer}>
          <GoogleLoginButton onClick={this.signUpWithGoogle} />
        </div>

        <form className={classes.form} onSubmit={ev => this.onSubmit(ev)}>
          <FormControl margin="normal" required fullWidth>
            <InputLabel htmlFor="email">Email</InputLabel>
            <Input
              id="email"
              name="email"
              autoComplete="email"
              autoFocus
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
            Sign Up
          </Button>
          <Typography className={classes.signUpLinkText}>
            Already have an account?&nbsp;
            {this.props.onNav ? (
              <Link onClick={this.props.onNav}>Login!</Link>
            ) : (
              <NextLink href="/login" passHref>
                <Link>Login!</Link>
              </NextLink>
            )}
          </Typography>
        </form>
      </React.Fragment>
    );
  }
}

const mapStateToProps = (appState: AppState) => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
    isSigningUp: appState.auth.isSigningUp,
  };
};

const mapDispatchToProps = dispatch =>
  bindActionCreators(
    {
      signup: (username: string, email: string, password: string) =>
        signupInitiated({ username, email, password }),
      signUpWithGoogle,
    },
    dispatch,
  );

export default withRouter(
  withStyles(styles)(connect(mapStateToProps, mapDispatchToProps)(SignupForm)),
);
