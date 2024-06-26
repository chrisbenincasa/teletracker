import {
  Avatar,
  Button,
  CircularProgress,
  createStyles,
  FormControl,
  Input,
  InputLabel,
  Link,
  makeStyles,
  Theme,
  Typography,
} from '@material-ui/core';
import LockOutlinedIcon from '@material-ui/icons/LockOutlined';
import React, {
  ChangeEvent,
  FormEvent,
  useCallback,
  useEffect,
  useState,
} from 'react';
import {
  loginInitiated,
  LoginRedirect,
  LoginState,
  logInWithGoogle as loginWithGoogleAction,
} from '../../actions/auth';
import GoogleLoginButton from './GoogleLoginButton';
import { GOOGLE_ACCOUNT_MERGE } from '../../constants/';
import { useRouter } from 'next/router';
import qs from 'querystring';
import _ from 'lodash';
import NextLink from 'next/link';
import { useStateSelectorWithPrevious } from '../../hooks/useStateSelector';
import { useDispatchAction } from '../../hooks/useDispatchAction';
import useIsMobile from '../../hooks/useIsMobile';

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
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
      zIndex: 900, // Above everything but below toolbar
    },
    socialSignInContainer: {
      marginTop: theme.spacing(2),
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
  }),
);

interface NewProps {
  readonly onSubmitted?: () => void;
  readonly onNav?: () => void;
  readonly onLogin?: (state?: LoginState) => void;
}

function LoginFormF(props: NewProps) {
  const classes = useStyles();
  const router = useRouter();
  const isMobile = useIsMobile();

  const params = new URLSearchParams(qs.stringify(router.query));

  let state: any = params
    .get('state')
    ?.split('-')
    .splice(1)
    .join('-');

  if (state) {
    try {
      state = JSON.parse(atob(state));
    } catch (e) {}
  }

  // If a user already has an account with email X and then attempts to sign
  // in with a federated identity (e.g. Google) our pre-signup lambda will
  // intercept and throw an error to indicate that it merged the federated
  // identity with the cognito user. This is returned in the form of an error
  // in the URL and indicates we should retry the federated login (because
  // with Cognito, the first attempt at a federated login with a matching
  // email causes an error)
  let error = params.get('error_description');

  const [isLoggingIn, wasLoggingIn] = useStateSelectorWithPrevious(
    state => state.auth.isLoggingIn,
  );
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const cameFromOAuth = params.get('code') != null;
  const mergedFederatedAccount = error
    ? error.includes(GOOGLE_ACCOUNT_MERGE)
    : false;
  const [isAuthed, wasAuthed] = useStateSelectorWithPrevious(
    state => !_.isUndefined(state.auth.token),
  );
  const authState = state ? (state as LoginState) : undefined;

  const dispatchLoginWithGoogle = useDispatchAction(loginWithGoogleAction);
  const dispatchLogin = useDispatchAction(loginInitiated);

  useEffect(() => {
    if (
      (!isLoggingIn && wasLoggingIn) ||
      (cameFromOAuth && isAuthed && !wasAuthed)
    ) {
      if (props.onLogin) {
        props.onLogin(authState);
      }
    }
  }, [isLoggingIn, cameFromOAuth, isAuthed]);

  const logInWithGoogle = useCallback(() => {
    let query = qs.stringify(router.query);
    let path = router.route;
    if (query) {
      path += `?${query};`;
    }

    let redirect: LoginRedirect | undefined;
    if (path !== '/login') {
      redirect = {
        route: path,
        asPath: router.asPath,
        query: router.query,
      };
    }

    dispatchLoginWithGoogle({
      state: {
        redirect,
      },
    });
  }, [router]);

  const onSubmit = (ev: FormEvent<HTMLFormElement>) => {
    ev.preventDefault();

    if (props.onSubmitted) {
      props.onSubmitted();
    }

    dispatchLogin({ email, password });

    // TODO: Protect this with some state.
    router.push('/');
  };

  const updateEmail = useCallback(
    (e: ChangeEvent<HTMLInputElement>) => {
      setEmail(e.target.value);
    },
    [email],
  );

  const updatePassword = useCallback(
    (e: ChangeEvent<HTMLInputElement>) => {
      setPassword(e.target.value);
    },
    [password],
  );

  return (
    <React.Fragment>
      {isLoggingIn || cameFromOAuth || mergedFederatedAccount ? (
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
        <GoogleLoginButton onClick={logInWithGoogle} />
      </div>
      <div>
        <form className={classes.form} onSubmit={onSubmit}>
          <FormControl margin="normal" required fullWidth>
            <InputLabel htmlFor="email">Email</InputLabel>
            <Input
              id="email"
              name="email"
              autoComplete="email"
              autoFocus={!isLoggingIn && !isMobile}
              type="email"
              onChange={updateEmail}
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
              onChange={updatePassword}
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
            {props.onNav ? (
              <Link onClick={props.onNav}>Signup!</Link>
            ) : (
              <NextLink href="/signup" passHref>
                <Link>Signup!</Link>
              </NextLink>
            )}
          </Typography>
        </form>
      </div>
    </React.Fragment>
  );
}

// class LoginForm extends Component<Props, State> {
//   constructor(props: Readonly<Props>) {
//     super(props);
//     const params = new URLSearchParams(qs.stringify(props.router.query));
//
//     let state: any = params
//       .get('state')
//       ?.split('-')
//       .splice(1)
//       .join('-');
//
//     if (state) {
//       try {
//         state = JSON.parse(atob(state));
//       } catch (e) {}
//     }
//
//     // If a user already has an account with email X and then attempts to sign
//     // in with a federated identity (e.g. Google) our pre-signup lambda will
//     // intercept and throw an error to indicate that it merged the federated
//     // identity with the cognito user. This is returned in the form of an error
//     // in the URL and indicates we should retry the federated login (because
//     // with Cognito, the first attempt at a federated login with a matching
//     // email causes an error)
//     let error = params.get('error_description');
//
//     this.state = {
//       email: '',
//       password: '',
//       cameFromOAuth: params.get('code') !== null,
//       mergedFederatedAccount: error
//         ? error.includes(GOOGLE_ACCOUNT_MERGE)
//         : false,
//       authState: state ? (state as LoginState) : undefined,
//     };
//   }
//
//   componentDidMount(): void {
//     if (!this.state.cameFromOAuth) {
//       ReactGA.pageview(window.location.pathname + window.location.search);
//     }
//
//     if (this.state.mergedFederatedAccount) {
//       this.logInWithGoogle();
//     }
//   }
//
//   componentDidUpdate(
//     prevProps: Readonly<Props>,
//     prevState: Readonly<State>,
//   ): void {
//     if (
//       (!this.props.isLoggingIn && prevProps.isLoggingIn) ||
//       (this.state.cameFromOAuth && this.props.isAuthed && !prevProps.isAuthed)
//     ) {
//       if (this.props.onLogin) {
//         this.props.onLogin(this.state.authState);
//       }
//     }
//   }
//
//   logInWithGoogle = () => {
//     let query = qs.stringify(this.props.router.query);
//     let path = this.props.router.route;
//     if (query) {
//       path += `?${query};`;
//     }
//
//     let redirect: LoginRedirect | undefined;
//     if (path !== '/login') {
//       redirect = {
//         route: path,
//         asPath: this.props.router.asPath,
//         query: this.props.router.query,
//       };
//     }
//
//     this.props.logInWithGoogle({
//       state: {
//         redirect,
//       },
//     });
//   };
//
//   onSubmit = (ev: FormEvent<HTMLFormElement>) => {
//     ev.preventDefault();
//
//     if (this.props.onSubmitted) {
//       this.props.onSubmitted();
//     }
//
//     this.props.login(this.state.email, this.state.password);
//
//     // TODO: Protect this with some state.
//     this.props.router.push('/');
//   };
//
//   render() {
//     let { classes, isLoggingIn } = this.props;
//     let { email, password } = this.state;
//
//     return (
//       <React.Fragment>
//         {this.props.isLoggingIn ||
//         this.state.cameFromOAuth ||
//         this.state.mergedFederatedAccount ? (
//           <div className={classes.overlay}>
//             <CircularProgress className={classes.progressSpinner} />
//             <div>
//               <Typography> Logging in&hellip;</Typography>
//             </div>
//           </div>
//         ) : null}
//         <Avatar className={classes.avatar}>
//           <LockOutlinedIcon />
//         </Avatar>
//         <Typography component="h1" variant="h5">
//           Log in
//         </Typography>
//
//         <div className={classes.socialSignInContainer}>
//           <GoogleLoginButton onClick={() => this.logInWithGoogle()} />
//         </div>
//         <div>
//           <form className={classes.form} onSubmit={this.onSubmit}>
//             <FormControl margin="normal" required fullWidth>
//               <InputLabel htmlFor="email">Email</InputLabel>
//               <Input
//                 id="email"
//                 name="email"
//                 autoComplete="email"
//                 autoFocus={!this.props.isLoggingIn}
//                 type="email"
//                 onChange={e => this.setState({ email: e.target.value })}
//                 disabled={isLoggingIn}
//                 value={email}
//               />
//             </FormControl>
//             <FormControl margin="normal" required fullWidth>
//               <InputLabel htmlFor="password">Password</InputLabel>
//               <Input
//                 id="password"
//                 name="password"
//                 autoComplete="password"
//                 type="password"
//                 onChange={e => this.setState({ password: e.target.value })}
//                 disabled={isLoggingIn}
//                 value={password}
//               />
//             </FormControl>
//
//             <Button
//               type="submit"
//               fullWidth
//               variant="contained"
//               color="primary"
//               className={classes.submit}
//             >
//               Log in
//             </Button>
//             <Typography className={classes.signUpLinkText}>
//               Don't have an account?&nbsp;
//               {this.props.onNav ? (
//                 <Link onClick={this.props.onNav}>Signup!</Link>
//               ) : (
//                 <NextLink href="/signup" passHref>
//                   <Link>Signup!</Link>
//                 </NextLink>
//               )}
//             </Typography>
//           </form>
//         </div>
//       </React.Fragment>
//     );
//   }
// }
//
// const mapStateToProps = (appState: AppState) => {
//   return {
//     isAuthed: !_.isUndefined(appState.auth.token),
//     isLoggingIn: appState.auth.isLoggingIn,
//   };
// };
//
// const mapDispatchToProps = dispatch =>
//   bindActionCreators(
//     {
//       login: (email: string, password: string) => login(email, password),
//       logInWithGoogle,
//       logInSuccessful: (token: string) => LoginSuccessful(token),
//     },
//     dispatch,
//   );
//
// export default withRouter(
//   withStyles(styles)(connect(mapStateToProps, mapDispatchToProps)(LoginForm)),
// );

export default LoginFormF;
