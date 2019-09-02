import React from 'react';
import { AppState } from '../reducers';
import { bindActionCreators, Dispatch } from 'redux';
import { Redirect, RouteComponentProps, withRouter } from 'react-router';
import { connect } from 'react-redux';
import {
  personFetchInitiated,
  PersonFetchInitiatedPayload,
} from '../actions/people/get_person';
import { Person } from '../types';
import * as R from 'ramda';
import {
  Chip,
  Grid,
  LinearProgress,
  CardMedia,
  createStyles,
  Fab,
  Hidden,
  InputLabel,
  Select,
  MenuItem,
  Theme,
  Typography,
  withStyles,
  WithStyles,
} from '@material-ui/core';
import { layoutStyles } from '../styles';
import { ResponsiveImage } from '../components/ResponsiveImage';
import AddToListDialog from '../components/AddToListDialog';
import imagePlaceholder from '../assets/images/imagePlaceholder.png';
import { Thing } from '../types';
import withUser, { WithUserProps } from '../components/withUser';
import ItemCard from '../components/ItemCard';
import { List as ListIcon } from '@material-ui/icons';

const styles = (theme: Theme) =>
  createStyles({
    layout: layoutStyles(theme),
    backdrop: {
      width: '100%',
      height: '100%',
      display: 'flex',
      zIndex: 1,
      background:
        'linear-gradient(to bottom, rgba(255, 255, 255,0) 0%,rgba(48, 48, 48,1) 100%)',
      //To do: integrate with theme styling for primary
    },
    descriptionContainer: {
      display: 'flex',
      flexDirection: 'column',
      marginBottom: 10,
    },
    genre: { margin: 5 },
    genreContainer: { display: 'flex', flexWrap: 'wrap' },
    itemCTA: {
      [theme.breakpoints.down('sm')]: {
        width: '80%',
      },
      width: '100%',
    },
    itemInformationContainer: {
      [theme.breakpoints.up('sm')]: {
        width: 250,
        marginLeft: 20,
      },
      display: 'flex',
      flex: '1 1 auto',
      backgroundColor: 'transparent',
      color: '#fff',
      flexDirection: 'column',
      position: 'relative',
    },
    itemDetailContainer: {
      margin: 20,
      display: 'flex',
      flex: '1 1 auto',
      color: '#fff',
      [theme.breakpoints.down('sm')]: {
        flexDirection: 'column',
      },
    },
    posterContainer: {
      [theme.breakpoints.up('sm')]: {
        width: 250,
      },
    },
  });

interface OwnProps {}

interface State {
  sortOrder: string;
  filter: number;
  manageTrackingModalOpen: boolean;
}

interface StateProps {
  isAuthed: boolean;
  person?: Person;
}

interface DispatchProps {
  personFetchInitiated: (id: PersonFetchInitiatedPayload) => void;
}

interface RouteProps {
  id: string;
}

type NotOwnProps = DispatchProps &
  RouteComponentProps<RouteProps> &
  WithStyles<typeof styles> &
  WithUserProps;

type Props = OwnProps & StateProps & NotOwnProps;

class PersonDetail extends React.Component<Props, State> {
  state: State = {
    sortOrder: 'Popularity',
    filter: -1,
    manageTrackingModalOpen: false,
  };

  componentDidMount() {
    this.props.personFetchInitiated({ id: this.props.match.params.id });
    // const { person } = this.props;
    // const { mainItemIndex } = this.state;

    // // Grab random item from filtered list of popular movies
    // if ((!prevProps.popular && popular) || (popular && mainItemIndex === -1)) {
    //   const highestRated = popular.filter(item => {
    //     const thing = thingsBySlug[item];
    //     const voteAverage = Number(getMetadataPath(thing, 'vote_average')) || 0;
    //     const voteCount = Number(getMetadataPath(thing, 'vote_count')) || 0;
    //     return voteAverage > 7 && voteCount > 1000;
    //   });

    //   const randomItem = Math.floor(Math.random() * highestRated.length);
    //   const popularItem = popular.findIndex(
    //     name => name === highestRated[randomItem],
    //   );

    //   this.setState({
    //     mainItemIndex: popularItem,
    //   });
    // }
  }

  setSortOrder = event => {
    console.log(event.target);
    this.setState({ sortOrder: event.target.value });
  };

  setFilter = genreId => {
    console.log(genreId);
    this.setState({ filter: genreId });
  };

  openManageTrackingModal = () => {
    this.setState({ manageTrackingModalOpen: true });
  };

  closeManageTrackingModal = () => {
    this.setState({ manageTrackingModalOpen: false });
  };

  renderLoading = () => {
    return (
      <div style={{ flexGrow: 1 }}>
        <LinearProgress />
      </div>
    );
  };

  renderTitle = (thing: Thing) => {
    return (
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'flex-start',
          width: '80%',
          marginBottom: 10,
        }}
      >
        <Typography color="inherit" variant="h4">
          {`${thing.name}`}
        </Typography>
      </div>
    );
  };

  renderFilmography = () => {
    const { classes, person, userSelf } = this.props;

    let filmography =
      (person &&
        person.metadata &&
        person.metadata['combined_credits'] &&
        person.metadata['combined_credits'].cast) ||
      [];
    filmography = filmography.sort((a, b) => {
      if (this.state.sortOrder === 'Popularity') {
        return a.popularity < b.popularity ? 1 : -1;
      } else if (this.state.sortOrder === 'Latest') {
        return a.release_date < b.release_date ? 1 : -1;
      } else if (this.state.sortOrder === 'Oldest') {
        return a.release_date > b.release_date ? 1 : -1;
      }
    });
    let genres = filmography
      .map(genre => genre.genre_ids)
      .flat()
      .reduce((accumulator, currentValue) => {
        if (
          accumulator.indexOf(currentValue) === -1 &&
          currentValue !== undefined
        ) {
          accumulator.push(currentValue);
        }
        return accumulator;
      }, []);
    console.log(genres);
    return (
      <div className={classes.genreContainer}>
        <Typography
          color="inherit"
          variant="h5"
          style={{ display: 'block', width: '100%' }}
        >
          Filmography
        </Typography>
        <div style={{ display: 'flex' }}>
          <div
            style={{
              display: 'flex',
              flexDirection: 'row',
              flexGrow: 1,
              flexWrap: 'wrap',
            }}
          >
            <Chip
              key={-1}
              label="All"
              className={classes.genre}
              onClick={() => this.setFilter(-1)}
              color={this.state.filter === -1 ? 'secondary' : 'inherit'}
            />
            {genres.map(genre => (
              <Chip
                key={genre}
                label={genre}
                className={classes.genre}
                onClick={() => this.setFilter(genre)}
                color={this.state.filter === genre ? 'secondary' : 'inherit'}
              />
            ))}
          </div>
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            <InputLabel shrink htmlFor="age-label-placeholder">
              Sort by:
            </InputLabel>
            <Select
              value={this.state.sortOrder}
              inputProps={{
                name: 'sortOrder',
                id: 'sort-order',
              }}
              onChange={this.setSortOrder}
            >
              <MenuItem value="Popularity">Popularity</MenuItem>
              <MenuItem value="Latest">Latest</MenuItem>
              <MenuItem value="Oldest">Oldest</MenuItem>
            </Select>
          </div>
        </div>
        <Grid container spacing={2}>
          {filmography.map(item =>
            item && item.poster_path ? (
              <ItemCard
                key={item.id}
                userSelf={userSelf}
                item={item}
                itemCardVisible={false}
                // addButton
              />
            ) : null,
          )}
        </Grid>
      </div>
    );
  };

  renderDescriptiveDetails = (thing: Thing) => {
    const { classes } = this.props;
    const { metadata } = thing;
    const biography = (metadata && metadata['biography']) || '';

    return (
      <div className={classes.descriptionContainer}>
        <div
          style={{
            display: 'flex',
            marginBottom: 8,
            flexDirection: 'column',
            alignItems: 'self-start',
            color: '#fff',
          }}
        >
          <Hidden only={['xs', 'sm']}>{this.renderTitle(thing)}</Hidden>
        </div>
        <div>
          <Typography color="inherit">{biography}</Typography>
        </div>
      </div>
    );
  };

  renderTrackingToggle = () => {
    const { classes } = this.props;
    let trackingCTA = 'Manage Tracking';

    return (
      <div className={classes.itemCTA}>
        <Fab
          size="small"
          variant="extended"
          aria-label="Add"
          onClick={this.openManageTrackingModal}
          style={{ marginTop: 5, width: '100%' }}
        >
          <ListIcon style={{ marginRight: 8 }} />
          {trackingCTA}
        </Fab>
      </div>
    );
  };

  renderPerson() {
    let { classes, person, userSelf } = this.props;
    const { manageTrackingModalOpen } = this.state;

    const backdrop =
      (person &&
        person.metadata &&
        person.metadata['combined_credits'] &&
        person.metadata['combined_credits'].cast[0]) ||
      {};

    const profilePath =
      (person && person.metadata && person.metadata['profile_path']) || '';

    console.log(person);
    return !person ? (
      this.renderLoading()
    ) : (
      <React.Fragment>
        <div className={classes.backdrop}>
          <ResponsiveImage
            item={backdrop}
            imageType="backdrop"
            imageStyle={{
              objectFit: 'cover',
              objectPosition: 'center top',
              width: '100%',
              height: '100%',
            }}
            pictureStyle={{
              position: 'absolute',
              width: '100%',
              height: 'auto',
              opacity: 0.2,
              filter: 'blur(3px)',
            }}
          />
          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
            }}
          />
          <div className={classes.itemDetailContainer}>
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                zIndex: 99999,
              }}
            >
              <Hidden smUp>{this.renderTitle(person)}</Hidden>
              <div className={classes.posterContainer}>
                <img
                  src={
                    profilePath
                      ? `https://image.tmdb.org/t/p/w185/${profilePath}`
                      : ''
                  }
                  style={{
                    width: '100%',
                  }}
                />
              </div>
              {this.renderTrackingToggle()}
              <AddToListDialog
                open={manageTrackingModalOpen}
                onClose={this.closeManageTrackingModal.bind(this)}
                userSelf={userSelf!}
                item={person}
              />
            </div>
            <div className={classes.itemInformationContainer}>
              {this.renderDescriptiveDetails(person)}
              {this.renderFilmography()}
            </div>
          </div>
        </div>
      </React.Fragment>
    );
  }

  render() {
    let { isAuthed } = this.props;

    return isAuthed ? (
      <div style={{ display: 'flex', flexGrow: 1 }}>{this.renderPerson()}</div>
    ) : (
      <Redirect to="/login" />
    );
  }
}

const mapStateToProps: (
  initialState: AppState,
  props: NotOwnProps,
) => (appState: AppState) => StateProps = (initial, props) => appState => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
    // isFetching: appState.itemDetail.fetching,
    person:
      appState.people.peopleById[props.match.params.id] ||
      appState.people.peopleBySlug[props.match.params.id],
  };
};

const mapDispatchToProps: (dispatch: Dispatch) => DispatchProps = dispatch =>
  bindActionCreators(
    {
      personFetchInitiated,
    },
    dispatch,
  );

export default withUser(
  withStyles(styles)(
    withRouter(
      connect(
        mapStateToProps,
        mapDispatchToProps,
      )(PersonDetail),
    ),
  ),
);
