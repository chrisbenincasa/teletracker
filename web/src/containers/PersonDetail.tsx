import {
  Button,
  CardMedia,
  CircularProgress,
  createStyles,
  Grid,
  Hidden,
  IconButton,
  LinearProgress,
  Theme,
  Typography,
  withStyles,
  WithStyles,
  withWidth,
} from '@material-ui/core';
import { fade } from '@material-ui/core/styles/colorManipulator';
import { ChevronLeft, ExpandLess, ExpandMore, Tune } from '@material-ui/icons';
import _ from 'lodash';
import * as R from 'ramda';
import { default as React } from 'react';
import ReactGA from 'react-ga';
import { Helmet } from 'react-helmet';
import InfiniteScroll from 'react-infinite-scroller';
import { connect } from 'react-redux';
import { RouteComponentProps, withRouter } from 'react-router';
import { bindActionCreators, Dispatch } from 'redux';
import {
  personCreditsFetchInitiated,
  PersonCreditsFetchInitiatedPayload,
} from '../actions/people/get_credits';
import {
  personFetchInitiated,
  PersonFetchInitiatedPayload,
} from '../actions/people/get_person';
import imagePlaceholder from '../assets/images/imagePlaceholder.png';
import CreateSmartListButton from '../components/Buttons/CreateSmartListButton';
import CreateDynamicListDialog from '../components/CreateDynamicListDialog';
import ActiveFilters from '../components/Filters/ActiveFilters';
import AllFilters from '../components/Filters/AllFilters';
import ItemCard from '../components/ItemCard';
import ManageTrackingButton from '../components/ManageTrackingButton';
import { ResponsiveImage } from '../components/ResponsiveImage';
import withUser, { WithUserProps } from '../components/withUser';
import { GA_TRACKING_ID } from '../constants/';
import { AppState } from '../reducers';
import { layoutStyles } from '../styles';
import { Genre, Network } from '../types';
import { Item } from '../types/v2/Item';
import { Person } from '../types/v2/Person';
import { filterParamsEqual } from '../utils/changeDetection';
import { collect } from '../utils/collection-utils';
import { DEFAULT_FILTER_PARAMS, FilterParams } from '../utils/searchFilters';
import { parseFilterParamsFromQs } from '../utils/urlHelper';

const styles = (theme: Theme) =>
  createStyles({
    layout: layoutStyles(theme),
    backdrop: {
      width: '100%',
      height: '100%',
      display: 'flex',
      zIndex: 1,
    },
    backdropContainer: {
      height: 'auto',
      overflow: 'hidden',
      top: 0,
      width: '100%',
      position: 'fixed',
    },
    backdropGradient: {
      position: 'absolute',
      top: 0,
      width: '100%',
      height: '100%',
      backgroundColor: 'rgba(48, 48, 48, 0.5)',
      backgroundImage:
        'linear-gradient(to bottom, rgba(255, 255, 255,0) 0%,rgba(48, 48, 48,1) 100%)',
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
      width: 'inherit',
      display: 'flex',
      flex: '0 1 auto',
      position: 'relative',
      '&:hover': {
        backgroundColor: fade(theme.palette.common.white, 0.25),
      },
    },
    settings: {
      display: 'flex',
      alignSelf: 'flex-end',
    },
    titleContainer: {
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'flex-start',
      width: '80%',
      [theme.breakpoints.down('sm')]: {
        width: '100%',
        alignItems: 'center',
      },
      marginBottom: 10,
      zIndex: 9999,
    },
    loadingCircle: {
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: 200,
      height: '100%',
    },
    fin: {
      fontStyle: 'italic',
      textAlign: 'center',
      margin: theme.spacing(6),
    },
  });

interface OwnProps {}

interface State {
  showFullBiography: boolean;
  showFilter: boolean;
  filters: FilterParams;
  // Indicates that the current person in state doesn't have the necessary info to show the full detail
  // page, so we need a full fetch.
  needsFetch: boolean;
  loadingCredits: boolean;
  createDynamicListDialogOpen: boolean;
  createPersonListDialogOpen: boolean;
}

interface StateProps {
  isAuthed: boolean;
  person?: Person;
  genres?: Genre[];
  networks?: Network[];
  loadingPerson: boolean;
  loadingCredits: boolean;
  creditsBookmark?: string;
  credits?: string[];
  itemById: { [key: string]: Item };
}

interface DispatchProps {
  personFetchInitiated: (id: PersonFetchInitiatedPayload) => void;
  fetchPersonCredits: (payload: PersonCreditsFetchInitiatedPayload) => void;
}

interface RouteProps {
  id: string;
}

interface WidthProps {
  width: string;
}

type NotOwnProps = DispatchProps &
  RouteComponentProps<RouteProps> &
  WithStyles<typeof styles> &
  WithUserProps &
  WidthProps;

type Props = OwnProps & StateProps & NotOwnProps;

class PersonDetail extends React.Component<Props, State> {
  constructor(props: Props) {
    super(props);
    let params = new URLSearchParams(window.location.search);

    let needsFetch;

    if (props.person) {
      needsFetch =
        _.isUndefined(props.person.cast_credits) ||
        props.person.cast_credits.data.length === 0 ||
        _.some(props.person.cast_credits.data, credit =>
          _.isUndefined(credit.item),
        );
    } else {
      needsFetch = true;
    }

    let defaultFilterParams = {
      ...DEFAULT_FILTER_PARAMS,
      sortOrder: 'recent',
    };

    let filterParams = R.mergeDeepRight(
      defaultFilterParams,
      R.filter(R.compose(R.not, R.isNil))(
        parseFilterParamsFromQs(props.location.search),
      ),
    ) as FilterParams;

    this.state = {
      showFullBiography: false,
      showFilter:
        params.has('sort') ||
        params.has('genres') ||
        params.has('networks') ||
        params.has('types'),
      filters: filterParams,
      needsFetch,
      loadingCredits: false,
      createDynamicListDialogOpen: false,
      createPersonListDialogOpen: false,
    };
  }

  componentDidMount() {
    const { isLoggedIn, userSelf } = this.props;

    if (this.state.needsFetch) {
      this.props.personFetchInitiated({ id: this.props.match.params.id });
    }

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

  componentDidUpdate(prevProps: Readonly<Props>): void {
    if (
      (!prevProps.person && this.props.person) ||
      (prevProps.loadingPerson && !this.props.loadingPerson)
    ) {
      this.setState({
        needsFetch: false,
      });
    }

    if (prevProps.loadingCredits && !this.props.loadingCredits) {
      this.setState({
        loadingCredits: false,
      });
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
    const { classes } = this.props;
    return (
      <div className={classes.titleContainer}>
        <Typography color="inherit" variant="h4">
          {`${person.name}`}
        </Typography>
      </div>
    );
  };

  toggleFilters = () => {
    this.setState({ showFilter: !this.state.showFilter });
  };

  loadCredits = () => {
    this.setState(
      {
        loadingCredits: true,
      },
      () => {
        this.props.fetchPersonCredits({
          personId: this.props.person!.id,
          filterParams: this.state.filters,
          limit: 18, // Use calculateLimit
          bookmark: this.props.creditsBookmark,
        });
      },
    );
  };

  loadMoreResults = () => {
    if (
      Boolean(this.props.credits) &&
      !this.props.loadingCredits &&
      !this.state.loadingCredits
    ) {
      this.loadCredits();
    }
  };

  handleFilterParamsChange = (filterParams: FilterParams) => {
    if (!filterParamsEqual(this.state.filters, filterParams)) {
      this.setState(
        {
          filters: filterParams,
        },
        () => {
          this.loadCredits();
        },
      );
    }
  };

  personFiltersForCreateDialog = (): FilterParams => {
    return {
      ...DEFAULT_FILTER_PARAMS,
      people: [this.props.person!.canonical_id],
    };
  };

  creditFiltersForCreateDialog = (): FilterParams => {
    return {
      ...this.state.filters,
      people: [
        ...(this.state.filters.people || []),
        this.props.person!.canonical_id,
      ],
    };
  };

  createListFromFilters = () => {
    this.setState({
      createDynamicListDialogOpen: true,
    });
  };

  createListForPerson = () => {
    this.setState({
      createPersonListDialogOpen: true,
    });
  };

  handleCreateDynamicModalClose = () => {
    this.setState({
      createDynamicListDialogOpen: false,
      createPersonListDialogOpen: false,
    });
  };

  renderLoadingCircle() {
    const { classes } = this.props;
    return (
      <div className={classes.loadingCircle}>
        <div>
          <CircularProgress color="secondary" />
        </div>
      </div>
    );
  }

  renderFilmography = () => {
    const { classes, genres, person, userSelf, credits, itemById } = this.props;
    const {
      filters: { genresFilter, itemTypes, sortOrder },
    } = this.state;

    let filmography: Item[];
    if (credits) {
      filmography = collect(credits, id => itemById[id]);
    } else {
      filmography = person!.cast_credits
        ? collect(person!.cast_credits.data, credit => credit.item)
        : [];
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
            updateFilters={this.handleFilterParamsChange}
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
          <CreateSmartListButton onClick={this.createListFromFilters} />
        </div>
        <AllFilters
          genres={genres}
          open={this.state.showFilter}
          filters={this.state.filters}
          updateFilters={this.handleFilterParamsChange}
          disableNetworks
          isListDynamic={false}
        />

        <InfiniteScroll
          pageStart={0}
          loadMore={this.loadMoreResults}
          hasMore={
            !Boolean(this.props.credits) || Boolean(this.props.creditsBookmark)
          }
          useWindow
          threshold={300}
        >
          <Grid container spacing={2}>
            {filmography.map(item =>
              item && item.posterImage ? (
                <ItemCard key={item.id} userSelf={userSelf} item={item} />
              ) : null,
            )}
          </Grid>
          {this.props.loadingCredits && this.renderLoadingCircle()}
          {!this.props.credits ||
            (!Boolean(this.props.creditsBookmark) && (
              <Typography className={classes.fin}>fin.</Typography>
            ))}
        </InfiniteScroll>
      </div>
    );
  };

  renderDescriptiveDetails = (person: Person) => {
    const { classes, width } = this.props;
    const { showFullBiography } = this.state;
    const biography = person.biography || '';
    const isMobile = ['xs', 'sm'].includes(width);
    const truncateSize = isMobile ? 300 : 1200;

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
            {showFullBiography ? biography : biography.substr(0, truncateSize)}
          </Typography>
          {biography.length > 1200 ? (
            <Button
              size="small"
              variant="contained"
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
            </Button>
          ) : null}
        </div>
      </div>
    );
  };

  renderPerson() {
    let { classes, person, loadingPerson, width } = this.props;
    let {
      needsFetch,
      createDynamicListDialogOpen,
      createPersonListDialogOpen,
    } = this.state;

    if (!person || loadingPerson || needsFetch) {
      return this.renderLoading();
    }

    const isMobile = ['xs', 'sm'].includes(width);
    const backdrop =
      person &&
      person.cast_credits &&
      person.cast_credits.data
        .map(character => character.item)
        .filter(item => {
          if (item && item.backdropImage) {
            return item;
          }
        })
        .slice(0, 1)[0];

    return (
      <React.Fragment>
        <Helmet>
          <title>{`${person.name} | Teletracker`}</title>
          <meta
            name="title"
            property="og:title"
            content={`${person.name} | Where to stream, rent, or buy. Track this person today!`}
          />
          <meta
            name="description"
            property="og:description"
            content={`Find out where to stream, rent, or buy content featuring ${person.name} online. Track it to find out when it's available on one of your services.`}
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
            content={`${person.name} - Where to Stream, Rent, or Buy their content`}
          />
          <meta
            name="twitter:description"
            content={`Find out where to stream, rent, or buy content featuring ${person.name} online. Track it to find out when it's available on one of your services.`}
          />
          <meta
            name="twitter:image"
            content={`https://image.tmdb.org/t/p/w342${person.profile_path}`}
          />
          <meta
            name="keywords"
            content={`${person.name}, stream, streaming, rent, buy, watch, track`}
          />
          <link
            rel="canonical"
            href={`http://teletracker.com${this.props.location.pathname}`}
          />
        </Helmet>
        <div className={classes.backdrop}>
          {backdrop && (
            <React.Fragment>
              <div className={classes.backdropContainer}>
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
                    display: 'block',
                    position: 'relative',
                    height: 'auto',
                  }}
                />
                <div className={classes.backdropGradient} />
              </div>
              <div
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'flex-start',
                }}
              >
                {!isMobile && (
                  <Button
                    size="small"
                    onClick={this.props.history.goBack}
                    variant="contained"
                    aria-label="Go Back"
                    style={{ marginTop: 20, marginLeft: 20 }}
                  >
                    <ChevronLeft style={{ marginRight: 8 }} />
                    Go Back
                  </Button>
                )}
                <div className={classes.personDetailContainer}>
                  <div className={classes.leftContainer}>
                    <Hidden smUp>{this.renderTitle(person)}</Hidden>
                    <div className={classes.posterContainer}>
                      <CardMedia
                        src={imagePlaceholder}
                        item={person}
                        component={ResponsiveImage}
                        imageType="profile"
                        imageStyle={{
                          width: '100%',
                          boxShadow: '7px 10px 23px -8px rgba(0,0,0,0.57)',
                        }}
                      />
                    </div>
                    <ManageTrackingButton
                      cta={'Track Actor'}
                      onClick={this.createListForPerson}
                    />
                  </div>
                  <div className={classes.personInformationContainer}>
                    {this.renderDescriptiveDetails(person)}
                    {this.renderFilmography()}
                  </div>
                </div>
              </div>
              <CreateDynamicListDialog
                filters={this.creditFiltersForCreateDialog()}
                open={createDynamicListDialogOpen}
                onClose={this.handleCreateDynamicModalClose}
                networks={this.props.networks || []}
                genres={this.props.genres || []}
              />
              <CreateDynamicListDialog
                filters={this.personFiltersForCreateDialog()}
                open={createPersonListDialogOpen}
                onClose={this.handleCreateDynamicModalClose}
                networks={this.props.networks || []}
                genres={this.props.genres || []}
                prefilledName={this.props.person!.name}
              />
            </React.Fragment>
          )}
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
    lists: appState.lists.listsById,
    loadingPerson: appState.people.loadingPeople,
    genres: appState.metadata.genres,
    networks: appState.metadata.networks,
    itemById: appState.itemDetail.thingsById,
    credits: appState.people.detail
      ? appState.people.detail.credits
      : undefined,
    creditsBookmark: appState.people.detail
      ? appState.people.detail.bookmark
      : undefined,
    loadingCredits: appState.people.detail
      ? appState.people.detail.loading
      : false,
  };
};

const mapDispatchToProps: (dispatch: Dispatch) => DispatchProps = dispatch =>
  bindActionCreators(
    {
      personFetchInitiated,
      fetchPersonCredits: personCreditsFetchInitiated,
    },
    dispatch,
  );

export default withWidth()(
  withUser(
    withStyles(styles)(
      withRouter(connect(mapStateToProps, mapDispatchToProps)(PersonDetail)),
    ),
  ),
);
