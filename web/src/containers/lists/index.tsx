import {
  Button,
  Card,
  CardContent,
  createStyles,
  CssBaseline,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Fab,
  Grid,
  Icon,
  LinearProgress,
  TextField,
  Theme,
  Typography,
  withStyles,
  WithStyles,
  CardMedia,
} from '@material-ui/core';
import * as R from 'ramda';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import { Link, Redirect } from 'react-router-dom';
import { bindActionCreators } from 'redux';
import {
  ListRetrieveAllInitiated,
  ListRetrieveAllPayload,
} from '../../actions/lists';
import withUser, { WithUserProps } from '../../components/withUser';
import { AppState } from '../../reducers';
import { layoutStyles } from '../../styles';
import { Thing } from "../../types";
import { List } from '../../types';
import { getPosterPath, getPosterUrl } from '../../utils/metadata-access';
import { ListsByIdMap } from '../../reducers/lists';

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
    cardMedia: {
      height: 0,
      width: '33.333%',
      paddingTop: '50%',
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
  listsById: ListsByIdMap;
  loadingLists: boolean;
}

interface DispatchProps {
  ListRetrieveAllInitiated: (payload?: ListRetrieveAllPayload) => void;
}

type Props = OwnProps &
  DispatchProps &
  WithStyles<typeof styles> &
  WithUserProps;

interface State {
  createDialogOpen: boolean;
}

type PlaceholderItem = { placeholder: true };
const makePlaceholder: () => PlaceholderItem = () => ({ placeholder: true });

class Lists extends Component<Props, State> {
  state: State = {
    createDialogOpen: false,
  };

  componentWillMount() {
    this.props.ListRetrieveAllInitiated();
  }

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

  renderPoster = (thing: Thing | PlaceholderItem, key: string | number) => {
    if ((thing as PlaceholderItem).placeholder) {
      return <div key={key} className={this.props.classes.cardMedia} />;
    } else {
      thing = thing as Thing;
      let posterUrl = getPosterUrl(thing, '154');

      if (posterUrl) {
        return (
          <CardMedia
            key={key}
            className={this.props.classes.cardMedia}
            image={posterUrl}
            title={thing.name}
          />
        );
      } else {
        return null;
      }
    }
  };

  renderItemPreviews = (list: List) => {
    let things: (Thing | PlaceholderItem)[] = list.things.slice(0, 6);

    if (things.length < 6) {
      let placeholdersNeeded = 6 - things.length;
      let placeholders: PlaceholderItem[] = Array(placeholdersNeeded).fill(
        makePlaceholder(),
      );
      things = things.concat(placeholders);
    }

    return (
      <div style={{ display: 'flex', flexWrap: 'wrap' }}>
        {things.map((thing, idx) => this.renderPoster(thing, idx))}
      </div>
    );
  };

  handleClickOpen = () => {
    this.setState({ createDialogOpen: true });
  };

  handleClose = () => {
    this.setState({ createDialogOpen: false });
  };

  renderList = (userList: List) => {
    let listWithDetails = this.props.listsById[userList.id];

    let list = listWithDetails || userList;

    return (
      <Grid key={list.id} sm={6} md={4} lg={4} item>
        <Card>
          {this.renderItemPreviews(list)}
          <CardContent>
            <Typography
              component={props => <Link {...props} to={'/lists/' + list.id} />}
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
  };

  renderLists() {
    if (
      this.props.retrievingUser ||
      !this.props.userSelf ||
      this.props.loadingLists
    ) {
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
                {userSelf.lists.map(this.renderList)}
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
    loadingLists: appState.lists.operation.inProgress,
    listsById: appState.lists.listsById,
  };
};

const mapDispatchToProps = dispatch =>
  bindActionCreators(
    {
      ListRetrieveAllInitiated,
    },
    dispatch,
  );

export default withUser(
  withStyles(styles)(
    connect(
      mapStateToProps,
      mapDispatchToProps,
    )(Lists),
  ),
);
