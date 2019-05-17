import {
  CssBaseline,
  Paper,
  Avatar,
  FormControl,
  Typography,
  InputLabel,
  Input,
  Button,
  createStyles,
  Theme,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import LockOutlinedIcon from '@material-ui/icons/LockOutlined';
import { push } from 'connected-react-router';
import * as R from 'ramda';
import React, { Component, FormEvent } from 'react';
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';
import { signup } from '../../actions/auth';
import { AppState } from '../../reducers';
import { Redirect } from 'react-router';

const styles = (theme: Theme) =>
  createStyles({
    main: {
      width: 'auto',
      display: 'block', // Fix IE 11 issue.
      marginLeft: theme.spacing.unit * 3,
      marginRight: theme.spacing.unit * 3,
      [theme.breakpoints.up(400 + theme.spacing.unit * 3 * 2)]: {
        width: 400,
        marginLeft: 'auto',
        marginRight: 'auto',
      },
    },
    paper: {
      marginTop: theme.spacing.unit * 8,
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      padding: `${theme.spacing.unit * 2}px ${theme.spacing.unit * 3}px ${theme
        .spacing.unit * 3}px`,
    },
    avatar: {
      margin: theme.spacing.unit,
      backgroundColor: theme.palette.secondary.main,
    },
    form: {
      width: '100%', // Fix IE 11 issue.
      marginTop: theme.spacing.unit,
    },
    submit: {
      marginTop: theme.spacing.unit * 3,
    },
  });

interface Props extends WithStyles<typeof styles> {
  isAuthed: boolean;
  signup: (username: string, email: string, password: string) => void;
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

  onSubmit(ev: FormEvent<HTMLFormElement>) {
    ev.preventDefault();

    this.props.signup(this.state.username, this.state.email, this.state.password);

    this.setState({
      username: '',
      email: '',
      password: '',
    });

    // TODO: Protect this with some state.
    push('/');
  }

  render() {
    let { isAuthed, classes } = this.props;
    let { username, email, password } = this.state;

    return !isAuthed ? (
      <main className={classes.main}>
        <CssBaseline />
        <Paper className={classes.paper}>
          <Avatar className={classes.avatar}>
            <LockOutlinedIcon />
          </Avatar>
          <Typography component="h1" variant="h5">
            Signup
          </Typography>

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
              <InputLabel htmlFor="username">Username</InputLabel>
              <Input
                id="username"
                name="username"
                autoComplete="username"
                type="text"
                onChange={e => this.setState({ username: e.target.value })}
                value={username}
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
              Signup
            </Button>
          </form>
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
      signup: (username: string, email: string, password: string) => signup(username, email, password),
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
