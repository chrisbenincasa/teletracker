import {
  Avatar,
  Button,
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
import { push } from 'connected-react-router';
import * as R from 'ramda';
import React, { Component, FormEvent } from 'react';
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';
import { signup, signUpWithGoogle } from '../../actions/auth';
import { AppState } from '../../reducers';
import { Link as RouterLink } from 'react-router-dom';
import * as firebase from 'firebase/app';
import GoogleLoginButton from './GoogleLoginButton';
import ReactGA from 'react-ga';
import { GA_TRACKING_ID } from '../../constants/';

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

  componentDidMount(): void {
    firebase
      .auth()
      .getRedirectResult()
      .then(result => {
        // TODO: do something with this...
      })
      .catch(console.error);

    ReactGA.initialize(GA_TRACKING_ID);
    ReactGA.pageview(window.location.pathname + window.location.search);
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

    // TODO: Protect this with some state.
    push('/');
  }

  render() {
    let { classes } = this.props;
    let { email, password } = this.state;

    return (
      <React.Fragment>
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
              <Link component={RouterLink} to="/login">
                Login!
              </Link>
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
  )(SignupForm),
);
