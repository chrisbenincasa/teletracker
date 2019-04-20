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
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  TextField,
  DialogActions,
} from '@material-ui/core';
import * as R from 'ramda';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import { Link, Redirect } from 'react-router-dom';
import { bindActionCreators } from 'redux';
import withUser, { WithUserProps } from '../../components/withUser';
import { AppState } from '../../reducers';
import { layoutStyles } from '../../styles';

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

interface OwnProps {
  isAuthed?: boolean;
  isCheckingAuth: boolean;
}

type Props = OwnProps & WithStyles<typeof styles> & WithUserProps;

interface State {
  createDialogOpen: boolean;
}

class Lists extends Component<Props, State> {
  state: State = {
    createDialogOpen: false,
  };

  refreshUser = () => {
    this.props.retrieveUser(true);
  };

  renderLoading() {
    return (
      <div style={{ flexGrow: 1 }}>
        <LinearProgress />
      </div>
    );
  }

  handleClickOpen = () => {
    this.setState({ createDialogOpen: true });
  };

  handleClose = () => {
    this.setState({ createDialogOpen: false });
  };

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
              <Fab
                color="primary"
                aria-label="Add"
                className={classes.fab}
                onClick={this.handleClickOpen}
              >
                <Icon>add</Icon>
              </Fab>
              <Dialog
                fullWidth
                maxWidth="xs"
                open={this.state.createDialogOpen}
              >
                <DialogTitle>Create a List</DialogTitle>
                <DialogContent>
                  {/* <DialogContentText>
                    To subscribe to this website, please enter your email
                    address here. We will send updates occasionally.
                  </DialogContentText> */}
                  <TextField
                    autoFocus
                    margin="dense"
                    id="name"
                    label="Name"
                    type="email"
                    fullWidth
                  />
                </DialogContent>
                <DialogActions>
                  <Button onClick={this.handleClose} color="primary">
                    Cancel
                  </Button>
                  <Button onClick={this.handleClose} color="primary">
                    Create
                  </Button>
                </DialogActions>
              </Dialog>
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
  };
};

const mapDispatchToProps = dispatch => bindActionCreators({}, dispatch);

export default withUser(
  withStyles(styles)(
    connect(
      mapStateToProps,
      mapDispatchToProps,
    )(Lists),
  ),
);
