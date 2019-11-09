import {
  createStyles,
  Fab,
  Grid,
  Hidden,
  IconButton,
  LinearProgress,
  Theme,
  Typography,
  withStyles,
  WithStyles,
} from '@material-ui/core';
import { ChevronLeft, ExpandLess, ExpandMore, Tune } from '@material-ui/icons';
import _ from 'lodash';
import * as R from 'ramda';
import { default as React } from 'react';
import ReactGA from 'react-ga';
import { Helmet } from 'react-helmet';
import { connect } from 'react-redux';
import { RouteComponentProps, withRouter } from 'react-router';
import { bindActionCreators, Dispatch } from 'redux';
import {
  personFetchInitiated,
  PersonFetchInitiatedPayload,
} from '../actions/people/get_person';
import ItemCard from '../components/ItemCard';
import ManageTracking from '../components/ManageTracking';
import { ResponsiveImage } from '../components/ResponsiveImage';
import withUser, { WithUserProps } from '../components/withUser';
import { GA_TRACKING_ID } from '../constants/';
import { AppState } from '../reducers';
import { layoutStyles } from '../styles';
import { Genre, ItemType, ListSortOptions, NetworkType } from '../types';
import { Person } from '../types/v2/Person';
import AllFilters from '../components/Filters/AllFilters';
import ActiveFilters from '../components/Filters/ActiveFilters';
import { FilterParams } from '../utils/searchFilters';
import { parseFilterParamsFromQs } from '../utils/urlHelper';

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
    listHeader: {
      margin: `${theme.spacing(2)}px 0`,
      display: 'flex',
      flex: '1 0 auto',
      alignItems: 'center',
    },
    listNameContainer: {
      display: 'flex',
      flex: '1 0 auto',
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
    settings: {
      display: 'flex',
      alignSelf: 'flex-end',
    },
  });

interface OwnProps {}

interface State {
  showFullBiography: boolean;
  showFilter: boolean;
  filters: FilterParams;
}

interface StateProps {
  isAuthed: boolean;
  person?: Person;
  genres?: Genre[];
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
  constructor(props: Props) {
    super(props);
    let params = new URLSearchParams(location.search);

    this.state = {
      showFullBiography: false,
      showFilter:
        params.has('sort') ||
        params.has('genres') ||
        params.has('networks') ||
        params.has('types'),
      filters: parseFilterParamsFromQs(this.props.location.search),
    };
  }

  componentDidMount() {
    const { isLoggedIn, userSelf } = this.props;

    this.props.personFetchInitiated({ id: this.props.match.params.id });

    ReactGA.initialize(GA_TRACKING_ID);
    ReactGA.pageview(window.location.pathname + window.location.search);

    if (
      isLoggedIn &&
      userSelf &&
      userSelf.user &&
      userSelf.user.getUsername()
    ) {
      ReactGA.set({ userId: userSelf.user.getUsername() });
    }
  }

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

  toggleFilters = () => {
    this.setState({ showFilter: !this.state.showFilter });
  };

  setFilters = (filters: FilterParams) => {
    this.setState({ filters });
  };

  setSortOrder = (sortOrder: ListSortOptions) => {
    if (this.state.filters.sortOrder !== sortOrder) {
      this.setState({
        filters: {
          ...this.state.filters,
          sortOrder,
        },
      });
    }
  };

  setType = (type?: ItemType[]) => {
    this.setState({
      filters: {
        ...this.state.filters,
        itemTypes: type,
      },
    });
  };

  setGenre = (genres?: number[]) => {
    this.setState({
      filters: {
        ...this.state.filters,
        genresFilter: genres,
      },
    });
  };

  setNetworks = (networks?: NetworkType[]) => {
    // Only update and hit endpoint if there is a state change
    if (this.state.filters.networks !== networks) {
      this.setState({
        filters: {
          ...this.state.filters,
          networks,
        },
      });
    }
  };

  renderFilmography = () => {
    const { classes, genres, person, userSelf } = this.props;
    const {
      filters: { genresFilter, itemTypes, networks, sortOrder },
    } = this.state;

    let filmography = person!.cast_credits || [];
    let filmographyFiltered = filmography
      .filter(
        credit =>
          (credit &&
            credit.item &&
            credit.item.genres &&
            credit.item.genres
              .map(g => g.id)
              .includes(
                (genresFilter && genresFilter.length > 0 && genresFilter[0]) ||
                  0,
              )) ||
          !genresFilter,
      )
      .filter(
        credit =>
          (credit &&
            credit.item &&
            credit.item.type.includes((itemTypes && itemTypes[0]) || '')) ||
          !itemTypes,
      )
      .sort((a, b) => {
        if (this.state.filters.sortOrder === 'popularity') {
          return (a.item!.popularity || 0.0) < (b.item!.popularity || 0.0)
            ? 1
            : -1;
        } else if (this.state.filters.sortOrder === 'recent') {
          let sort;
          if (!b.item!.release_date) {
            sort = 1;
          } else if (!a.item!.release_date) {
            sort = -1;
          } else {
            sort = a.item!.release_date < b.item!.release_date ? 1 : -1;
          }
          return sort;
        } else {
          return 0;
        }
      });

    let finalGenres: Genre[] = [];

    if (this.props.genres) {
      finalGenres = _.chain(filmography)
        .map(f => f.item!.genres || [])
        .flatten()
        .map(f => f.id)
        .uniq()
        .map(id => {
          let g = _.find(this.props.genres, g => g.id === id);
          if (g) {
            return [g];
          } else {
            return [];
          }
        })
        .flatten()
        .sortBy(g => g.name)
        .value();
    }

    return (
      <div className={classes.genreContainer}>
        <div className={classes.listHeader}>
          <div className={classes.listNameContainer}>
            <Typography
              color="inherit"
              variant="h5"
              style={{ display: 'block', width: '100%' }}
            >
              Filmography
            </Typography>
          </div>
          <ActiveFilters
            genres={genres}
            updateFilters={this.setFilters}
            filters={this.state.filters}
            isListDynamic={false}
          />
          <IconButton
            onClick={this.toggleFilters}
            className={classes.settings}
            color={this.state.showFilter ? 'secondary' : 'inherit'}
          >
            <Tune />
            <Typography variant="srOnly">Tune</Typography>
          </IconButton>
        </div>
        <AllFilters
          genres={genres}
          open={this.state.showFilter}
          handleTypeChange={this.setType}
          handleGenreChange={this.setGenre}
          handleSortChange={this.setSortOrder}
          isListDynamic={false}
        />

        <Grid container spacing={2}>
          {filmographyFiltered.map(credit =>
            credit && credit.item!.posterImage ? (
              <ItemCard
                key={credit.id}
                userSelf={userSelf}
                item={credit.item!}
              />
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
          {biography.length > 1200 ? (
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
          ) : null}
        </div>
      </div>
    );
  };

  renderPerson() {
    let { classes, person, userSelf } = this.props;

    if (!person) {
      return this.renderLoading();
    }

    const backdrop = person!.cast_credits![0];
    const profilePath = person!.profile_path || '';

    return (
      <React.Fragment>
        <Helmet>
          <title>{`${person.name} | Teletracker`}</title>
          <meta
            name="title"
            property="og:title"
            content={`${
              person.name
            } | Where to stream, rent, or buy. Track this person today!`}
          />
          <meta
            name="description"
            property="og:description"
            content={`Find out where to stream, rent, or buy content featuring ${
              person.name
            } online. Track it to find out when it's available on one of your services.`}
          />
          <meta
            name="image"
            property="og:image"
            content={`https://image.tmdb.org/t/p/w342${person.profile_path}`}
          />
          <meta property="og:type" content="video.movie" />
          <meta property="og:image:type" content="image/jpg" />
          <meta property="og:image:width" content="342" />
          <meta
            data-react-helmet="true"
            property="og:image:height"
            content="513"
          />
          <meta
            property="og:url"
            content={`http://teletracker.com${this.props.location.pathname}`}
          />
          <meta
            data-react-helmet="true"
            name="twitter:card"
            content="summary"
          />
          <meta
            name="twitter:title"
            content={`${
              person.name
            } - Where to Stream, Rent, or Buy their content`}
          />
          <meta
            name="twitter:description"
            content={`Find out where to stream, rent, or buy content featuring ${
              person.name
            } online. Track it to find out when it's available on one of your services.`}
          />
          <meta
            name="twitter:image"
            content={`https://image.tmdb.org/t/p/w342${person.profile_path}`}
          />
          <meta
            name="keywords"
            content={`${
              person.name
            }, stream, streaming, rent, buy, watch, track`}
          />
          <link
            rel="canonical"
            href={`http://teletracker.com${this.props.location.pathname}`}
          />
        </Helmet>
        <div className={classes.backdrop}>
          <ResponsiveImage
            item={backdrop.item!}
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
                <ManageTracking itemDetail={person} />
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
    return (
      <div style={{ display: 'flex', flexGrow: 1 }}>{this.renderPerson()}</div>
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
    genres: appState.metadata.genres,
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
