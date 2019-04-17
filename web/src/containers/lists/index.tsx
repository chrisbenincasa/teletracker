import React, { Component } from 'react';
import {
  Typography,
  Theme,
  withStyles,
  createStyles,
  CssBaseline,
  WithStyles,
  Grid,
  Card,
  CardContent,
  LinearProgress,
} from '@material-ui/core';
import { AddCircleOutline } from '@material-ui/icons';
import { layoutStyles } from '../../styles';
import classNames from 'classnames';
import { AppState } from '../../reducers';
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';
import { retrieveUser } from '../../actions/user';
import { User } from '../../types';
import * as R from 'ramda';
import { Redirect } from 'react-router-dom';
import _ from 'lodash';

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
  });

interface Props extends WithStyles<typeof styles> {
  isAuthed?: boolean;
  isCheckingAuth: boolean;
  retrievingUser: boolean;
  userSelf?: User;
  retrieveUser: () => void;
}

class Lists extends Component<Props> {
  componentDidMount() {
    this.loadUser(this.props);
  }

  componentWillReceiveProps(newProps: Props) {
    this.loadUser(newProps);
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
      this.props.retrieveUser();
    }
  }, 100);

  renderLoading() {
    let { classes } = this.props;

    return (
      <div style={{ flexGrow: 1 }}>
        <LinearProgress />
      </div>
    );
  }

  renderLists() {
    let { classes } = this.props;

    if (this.props.retrievingUser || !this.props.userSelf) {
      return this.renderLoading();
    } else {
      return (
        <div>
          <CssBaseline />
          <div className={classes.layout}>
            <Typography component="h3" variant="h3">
              Lists for {this.props.userSelf!.name}
            </Typography>

            <div className={classes.cardGrid}>
              <Grid container spacing={16}>
                <Grid key="create-new" sm={6} md={4} lg={4} item>
                  <Card className={this.props.classes.card}>
                    <CardContent
                      className={classNames(
                        classes.cardContent,
                        classes.addNewCard,
                      )}
                    >
                      <div style={{ width: '100%', paddingBottom: '20px' }}>
                        <AddCircleOutline
                          style={{
                            fontSize: 50,
                            margin: '0 auto',
                            display: 'block',
                          }}
                        />
                      </div>
                      <Typography component="h3" variant="h5">
                        Create new list
                      </Typography>
                    </CardContent>
                  </Card>
                </Grid>
                <Grid sm={6} md={4} lg={4} item>
                  <Card>
                    <CardContent />
                  </Card>
                </Grid>
                <Grid sm={6} md={4} lg={4} item>
                  <Card>
                    <CardContent />
                  </Card>
                </Grid>
                <Grid sm={6} md={4} lg={4} item>
                  <Card>
                    <CardContent />
                  </Card>
                </Grid>
              </Grid>
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
