import {
  Button,
  Card,
  CardContent,
  createStyles,
  CssBaseline,
  Fab,
  Grid,
  Icon,
  LinearProgress,
  Theme,
  Typography,
  withStyles,
  WithStyles,
} from '@material-ui/core';
import _ from 'lodash';
import * as R from 'ramda';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import { Redirect, Link } from 'react-router-dom';
import { bindActionCreators } from 'redux';
import { retrieveUser } from '../../actions/user';
import { AppState } from '../../reducers';
import { layoutStyles } from '../../styles';
import { User } from '../../types';

const styles = (theme: Theme) =>
  createStyles({
    layout: layoutStyles(theme),
    cardGrid: {
      padding: `${theme.spacing.unit * 8}px 0`,
    },
    card: {
      height: '100%',
      display: 'flex',
      flexDirection: 'column',
    },
    cardContent: {
      flexGrow: 1,
    },
    addNewCard: {
      display: 'flex',
      flexWrap: 'wrap',
      justifyContent: 'center',
    },
    fab: {
      margin: theme.spacing.unit,
    },
  });

interface Props extends WithStyles<typeof styles> {
  isAuthed?: boolean;
  isCheckingAuth: boolean;
  retrievingUser: boolean;
  userSelf?: User;
  retrieveUser: (force: boolean) => void;
}

class Lists extends Component<Props> {
  componentDidMount() {
    this.loadUser(this.props);
  }

  componentDidUpdate(oldProps: Props) {
    if (oldProps.isCheckingAuth && this.props.isCheckingAuth) {
      this.loadUser(this.props);
    }
  }

  // This is stupid gnarly. We make a debounced check here for the following edge case:
  // After logging in, componentDidMount and componentWillReceiveProps fire in very quick succession
  // This means it's possible to fire off 2 API calls to retrieve the current user.
  // However, we need both componentDidMount and componentWillReceiveProps to call this because:
  // 1. with just componentWillReceiveProps, switching from "Home" to "Lists" will not initiate a request for the user
  // 2. with just componentDidMount, loading directly to /lists will not update the page once isCheckingAuth is true
  loadUser = _.debounce((props: Props) => {
    if (
      !props.isCheckingAuth &&
      !this.props.userSelf &&
      !this.props.retrievingUser
    ) {
      this.props.retrieveUser(false);
    }
  }, 100);

  refreshUser = () => {
    this.props.retrieveUser(true);
  };

  renderLoading() {
    let { classes } = this.props;

    return (
      <div style={{ flexGrow: 1 }}>
        <LinearProgress />
      </div>
    );
  }

  renderLists() {
    if (this.props.retrievingUser || !this.props.userSelf) {
      return this.renderLoading();
    } else {
      let { classes, userSelf } = this.props;
      return (
        <div>
          <CssBaseline />
          <div className={classes.layout}>
            <Typography component="h4" variant="h4">
              Lists for {userSelf.name}
            </Typography>

            <div className={classes.cardGrid}>
              <Button onClick={this.refreshUser}>Refresh</Button>
              <Grid container spacing={16}>
                {userSelf.lists.map(list => {
                  return (
                    <Grid key={list.id} sm={6} md={4} lg={4} item>
                      <Card>
                        <CardContent>
                          <Typography
                            component={props => (
                              <Link {...props} to={'/lists/' + list.id} />
                            )}
                            variant="h5"
                          >
                            {list.name}
                          </Typography>
                          {list.things.length == 0 || list.things.length > 1
                            ? list.things.length + ' items'
                            : list.things.length + ' item'}
                        </CardContent>
                      </Card>
                    </Grid>
                  );
                })}
              </Grid>
              <Fab color="primary" aria-label="Add" className={classes.fab}>
                <Icon>add</Icon>
              </Fab>
            </div>
          </div>
        </div>
      );
    }
  }

  render() {
    return this.props.isAuthed ? this.renderLists() : <Redirect to="/login" />;
  }
}

const mapStateToProps = (appState: AppState) => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
    isCheckingAuth: appState.auth.checkingAuth,
    retrievingUser: appState.userSelf.retrievingSelf,
    userSelf: appState.userSelf.self,
  };
};

const mapDispatchToProps = dispatch =>
  bindActionCreators(
    {
      retrieveUser,
    },
    dispatch,
  );

export default withStyles(styles)(
  connect(
    mapStateToProps,
    mapDispatchToProps,
  )(Lists),
);
