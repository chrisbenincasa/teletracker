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
import { signupInitiated, signUpWithGoogle } from '../actions/auth';
import { AppState } from '../reducers';
import SignupForm from '../components/Auth/SignupForm';
import { withRouter } from 'next/router';
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
    },
  });

interface Props extends WithStyles<typeof styles>, WithRouterProps {
  isAuthed: boolean;
  signup: (username: string, email: string, password: string) => void;
  signUpWithGoogle: () => any;
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

  render() {
    let { isAuthed, classes, router } = this.props;
    if (isAuthed) {
      router.replace('/');
    }

    return (
      <div className={classes.main}>
        <Paper className={classes.paper}>
          <SignupForm />
        </Paper>
      </div>
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
        signupInitiated({ username, email, password }),
      signUpWithGoogle,
    },
    dispatch,
  );

export default withRouter(
  withStyles(styles)(connect(mapStateToProps, mapDispatchToProps)(Signup)),
);
