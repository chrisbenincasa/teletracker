import {
  Button,
  Card,
  CardContent,
  CardMedia,
  createStyles,
  CssBaseline,
  Dialog,
  DialogTitle,
  Grid,
  Icon,
  LinearProgress,
  List,
  ListItem,
  ListItemText,
  Theme,
  Typography,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import classNames from 'classnames';
import * as R from 'ramda';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import { Redirect } from 'react-router-dom';
import Truncate from 'react-truncate';
import { bindActionCreators } from 'redux';
import withUser, { WithUserProps } from '../../components/withUser';
import { AppState } from '../../reducers';
import { layoutStyles } from '../../styles';
import { Thing } from '../../types/external/themoviedb/Movie';
import { getDescription, getPosterPath } from '../../utils/metadata-access';

const styles = (theme: Theme) =>
  createStyles({
    layout: layoutStyles(theme),
    cardGrid: {
      padding: `${theme.spacing.unit * 8}px 0`,
    },
    title: {
      whiteSpace: 'nowrap',
      overflow: 'hidden',
      textOverflow: 'ellipsis',
    },
    card: {
      height: '100%',
      display: 'flex',
      flexDirection: 'column',
    },
    cardMedia: {
      height: 0,
      width: '100%',
      paddingTop: '150%',
    },
    cardContent: {
      flexGrow: 1,
    },
    paper: {
      position: 'absolute',
      width: theme.spacing.unit * 50,
      backgroundColor: theme.palette.background.paper,
      boxShadow: theme.shadows[5],
      padding: theme.spacing.unit * 4,
      outline: 'none',
    },
  });

interface Props extends WithStyles<typeof styles> {
  isAuthed: boolean;
  isSearching: boolean;
  searchResults?: Thing[];
}

interface State {
  modalOpen: boolean;
}

class Home extends Component<Props & WithUserProps, State> {
  state = {
    modalOpen: false,
  };

  renderLoading = () => {
    let { classes } = this.props;

    return (
      <div style={{ flexGrow: 1 }}>
        <LinearProgress />
      </div>
    );
  };

  renderPoster = (thing: Thing) => {
    let poster = getPosterPath(thing);
    if (poster) {
      return (
        <CardMedia
          className={this.props.classes.cardMedia}
          image={'https://image.tmdb.org/t/p/w300' + poster}
          title={thing.name}
        />
      );
    } else {
      return null;
    }
  };

  handleModalOpen = () => {
    this.setState({ modalOpen: true });
  };

  handleModalClose = () => {
    this.setState({ modalOpen: false });
  };

  renderSearchResults = () => {
    let { searchResults, classes } = this.props;
    searchResults = searchResults || [];

    return this.props.isSearching ? (
      this.renderLoading()
    ) : (
      <main>
        <CssBaseline />
        {searchResults.length ? (
          <div className={classNames(classes.layout, classes.cardGrid)}>
            <Grid container spacing={16}>
              {searchResults.map(result => {
                return (
                  <Grid key={result.id} sm={6} md={4} lg={3} item>
                    <Card className={this.props.classes.card}>
                      {this.renderPoster(result)}
                      <CardContent className={classes.cardContent}>
                        <Typography
                          className={classes.title}
                          gutterBottom
                          variant="h5"
                          component="h2"
                        >
                          {result.name}
                        </Typography>
                        <Typography>
                          <Truncate lines={3} ellipsis={<span>...</span>}>
                            {getDescription(result)}
                          </Truncate>
                        </Typography>
                        <Button
                          variant="contained"
                          color="primary"
                          onClick={this.handleModalOpen}
                        >
                          <Icon>playlist_add</Icon>
                          <Typography color="inherit">Add to List</Typography>
                        </Button>
                      </CardContent>
                    </Card>
                  </Grid>
                );
              })}
            </Grid>
            <Dialog
              aria-labelledby="simple-modal-title"
              aria-describedby="simple-modal-description"
              open={this.state.modalOpen}
              onClose={this.handleModalClose}
              fullWidth
              maxWidth="xs"
            >
              <DialogTitle id="simple-dialog-title">Choose a list</DialogTitle>

              <div>
                <List>
                  {this.props.userSelf!.lists.map(list => (
                    <ListItem button key={list.id}>
                      <ListItemText primary={list.name} />
                    </ListItem>
                  ))}
                </List>
              </div>
            </Dialog>
          </div>
        ) : null}
      </main>
    );
  };

  render() {
    return this.props.isAuthed ? (
      this.renderSearchResults()
    ) : (
      <Redirect to="/login" />
    );
  }
}

const mapStateToProps = (appState: AppState) => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
    isSearching: appState.search.searching,
    searchResults: R.path<Thing[]>(['search', 'results', 'data'], appState),
  };
};

const mapDispatchToProps = dispatch => bindActionCreators({}, dispatch);

export default withUser(
  withStyles(styles)(
    connect(
      mapStateToProps,
      mapDispatchToProps,
    )(Home),
  ),
);
