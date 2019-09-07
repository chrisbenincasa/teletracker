import React from 'react';
import { AppState } from '../reducers';
import { bindActionCreators, Dispatch } from 'redux';
import { Redirect, RouteComponentProps, withRouter } from 'react-router';
import { connect } from 'react-redux';
import {
  personFetchInitiated,
  PersonFetchInitiatedPayload,
} from '../actions/people/get_person';
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
import withUser, { WithUserProps } from '../components/withUser';
import ItemCard from '../components/ItemCard';
import {
  ChevronLeft,
  List as ListIcon,
  ExpandMore,
  ExpandLess,
} from '@material-ui/icons';
import Thing from '../types/Thing';
import Person from '../types/Person';

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
    filterSortContainer: {
      [theme.breakpoints.up('sm')]: {
        display: 'flex',
      },
      marginBottom: 8,
      flexGrow: 1,
    },
    genre: { margin: 5 },
    genreContainer: { display: 'flex', flexWrap: 'wrap' },
    leftContainer: {
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      [theme.breakpoints.up('sm')]: {
        position: 'sticky',
        top: 75,
        height: 475,
      },
    },
    personCTA: {
      [theme.breakpoints.down('sm')]: {
        width: '80%',
      },
      width: '100%',
    },
    personInformationContainer: {
      [theme.breakpoints.up('sm')]: {
        marginLeft: 20,
      },
      display: 'flex',
      flex: '1 1 auto',
      backgroundColor: 'transparent',
      color: '#fff',
      flexDirection: 'column',
      position: 'relative',
    },
    personDetailContainer: {
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
      width: '90%',
    },
  });

interface OwnProps {}

interface State {
  sortOrder: string;
  filter: number;
  manageTrackingModalOpen: boolean;
  showFullBiography: boolean;
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
    showFullBiography: false,
  };

  componentDidMount() {
    this.props.personFetchInitiated({ id: this.props.match.params.id });
  }

  setSortOrder = event => {
    this.setState({ sortOrder: event.target.value });
  };

  setFilter = genreId => {
    this.setState({ filter: genreId });
  };

  openManageTrackingModal = () => {
    this.setState({ manageTrackingModalOpen: true });
  };

  closeManageTrackingModal = () => {
    this.setState({ manageTrackingModalOpen: false });
  };

  showFullBiography = () => {
    this.setState({ showFullBiography: !this.state.showFullBiography });
  };

  renderLoading = () => {
    return (
      <div style={{ flexGrow: 1 }}>
        <LinearProgress />
      </div>
    );
  };

  renderTitle = (person: Person) => {
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
          {`${person.name}`}
        </Typography>
      </div>
    );
  };

  renderFilmography = () => {
    const { classes, person, userSelf } = this.props;

    let filmography = person!.castMemberOf;

    let filmographyFiltered = filmography
      .filter(
        item =>
          (item &&
            item.genreIds &&
            item.genreIds.includes(this.state.filter)) ||
          this.state.filter === -1,
      )
      .sort((a, b) => {
        if (this.state.sortOrder === 'Popularity') {
          return (a.popularity || 0.0) < (b.popularity || 0.0) ? 1 : -1;
        } else if (
          this.state.sortOrder === 'Latest' ||
          this.state.sortOrder === 'Oldest'
        ) {
          let sort;
          if (!b.releaseDate) {
            sort = 1;
          } else if (!a.releaseDate) {
            sort = -1;
          } else {
            sort = a.releaseDate < b.releaseDate ? 1 : -1;
          }

          return this.state.sortOrder === 'Oldest' ? -sort : sort;
        } else {
          return 0;
        }
      });
    let genres = filmography
      .map(genre => genre.genreIds)
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
    return (
      <div className={classes.genreContainer}>
        <Typography
          color="inherit"
          variant="h5"
          style={{ display: 'block', width: '100%' }}
        >
          Filmography
        </Typography>
        <div className={classes.filterSortContainer}>
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
              color={this.state.filter === -1 ? 'secondary' : 'default'}
            />
            {genres.map(genre => (
              <Chip
                key={genre}
                label={genre}
                className={classes.genre}
                onClick={() => this.setFilter(genre)}
                color={this.state.filter === genre ? 'secondary' : 'default'}
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
          {filmographyFiltered.map(item =>
            item && item.posterPath ? (
              <ItemCard key={item.id} userSelf={userSelf} item={item} />
            ) : null,
          )}
        </Grid>
      </div>
    );
  };

  renderDescriptiveDetails = (person: Person) => {
    const { classes } = this.props;
    const { showFullBiography } = this.state;

    const biography = person.biography || '';

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
          <Hidden only={['xs', 'sm']}>{this.renderTitle(person)}</Hidden>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          <Typography color="inherit">
            {showFullBiography ? biography : biography.substr(0, 1200)}
          </Typography>
          <Fab
            size="small"
            variant="extended"
            aria-label={showFullBiography ? 'Read Less' : 'Read More'}
            onClick={this.showFullBiography}
            style={{ marginTop: 5, display: 'flex', alignSelf: 'center' }}
          >
            {showFullBiography ? (
              <ExpandLess style={{ marginRight: 8 }} />
            ) : (
              <ExpandMore style={{ marginRight: 8 }} />
            )}
            {showFullBiography ? 'Read Less' : 'Read More'}
          </Fab>
        </div>
      </div>
    );
  };

  renderTrackingToggle = () => {
    const { classes } = this.props;
    let trackingCTA = 'Manage Tracking';

    return (
      <div className={classes.personCTA}>
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

    if (!person) {
      return this.renderLoading();
    }

    const backdrop = person!.castMemberOf[0];

    const profilePath = person!.profilePath || '';

    return (
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
              alignItems: 'flex-start',
            }}
          >
            <Fab
              size="small"
              onClick={this.props.history.goBack}
              variant="extended"
              aria-label="Go Back"
              style={{ marginTop: 20, marginLeft: 20 }}
            >
              <ChevronLeft style={{ marginRight: 8 }} />
              Go Back
            </Fab>

            <div className={classes.personDetailContainer}>
              <div className={classes.leftContainer}>
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
                {/*<AddToListDialog*/}
                {/*  open={manageTrackingModalOpen}*/}
                {/*  onClose={this.closeManageTrackingModal.bind(this)}*/}
                {/*  userSelf={userSelf!}*/}
                {/*  item={person}*/}
                {/*/>*/}
              </div>
              <div className={classes.personInformationContainer}>
                {this.renderDescriptiveDetails(person)}
                {this.renderFilmography()}
              </div>
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
