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
import CreateDynamicListDialog from '../components/Dialogs/CreateDynamicListDialog';
import ActiveFilters from '../components/Filters/ActiveFilters';
import AllFilters from '../components/Filters/AllFilters';
import ItemCard from '../components/ItemCard';
import ManageTrackingButton from '../components/Buttons/ManageTrackingButton';
import { ResponsiveImage } from '../components/ResponsiveImage';
import withUser, { WithUserProps } from '../components/withUser';
import { AppState } from '../reducers';
import { Genre, Network } from '../types';
import { Item } from '../types/v2/Item';
import { Person } from '../types/v2/Person';
import { filterParamsEqual } from '../utils/changeDetection';
import { collect } from '../utils/collection-utils';
import { DEFAULT_FILTER_PARAMS, FilterParams } from '../utils/searchFilters';
import { parseFilterParamsFromQs } from '../utils/urlHelper';
import qs from 'querystring';
import withRouter, { WithRouterProps } from 'next/dist/client/with-router';

const styles = (theme: Theme) =>
  createStyles({
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
      [theme.breakpoints.down('sm')]: {
        height: '100%',
      },
    },
    backdropGradient: {
      position: 'absolute',
      top: 0,
      width: '100%',
      height: '100%',
      backgroundColor: theme.custom.backdrop.backgroundColor,
      backgroundImage: theme.custom.backdrop.backgroundImage,
    },
    descriptionContainer: {
      display: 'flex',
      flexDirection: 'column',
      marginBottom: theme.spacing(1),
    },
    filters: {
      display: 'flex',
      flexDirection: 'row',
      marginBottom: theme.spacing(1),
      justifyContent: 'flex-end',
      alignItems: 'center',
    },
    filterSortContainer: {
      marginBottom: theme.spacing(1),
      flexGrow: 1,
      [theme.breakpoints.up('sm')]: {
        display: 'flex',
      },
    },
    genre: {
      margin: theme.spacing(1),
    },
    genreContainer: {
      display: 'flex',
      flexWrap: 'wrap',
      flexDirection: 'column',
    },
    leftContainer: {
      display: 'flex',
      flexDirection: 'column',
      position: 'relative',
      [theme.breakpoints.up('md')]: {
        position: 'sticky',
        top: 75,
        height: 475,
      },
    },
    listHeader: {
      margin: theme.spacing(2, 0),
      display: 'flex',
      flex: '1 0 auto',
      alignItems: 'center',
    },
    listNameContainer: {
      display: 'flex',
      flex: '1 0 auto',
    },
    personCTA: {
      width: '100%',
      [theme.breakpoints.down('sm')]: {
        width: '80%',
      },
    },
    personInformationContainer: {
      display: 'flex',
      flex: '1 1 auto',
      backgroundColor: 'transparent',
      color: '#fff',
      flexDirection: 'column',
      position: 'relative',
      [theme.breakpoints.up('sm')]: {
        marginLeft: theme.spacing(3),
      },
    },
    personDetailContainer: {
      margin: theme.spacing(3),
      display: 'flex',
      flex: '1 1 auto',
      color: '#fff',
      [theme.breakpoints.down('sm')]: {
        flexDirection: 'column',
        margin: theme.spacing(1),
      },
    },
    posterContainer: {
      margin: '0 auto',
      width: '50%',
      position: 'relative',
      '&:hover': {
        backgroundColor: fade(theme.palette.common.white, 0.25),
      },
      [theme.breakpoints.up('sm')]: {
        width: 250,
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
      marginBottom: theme.spacing(1),
      zIndex: theme.zIndex.mobileStepper,
      [theme.breakpoints.down('sm')]: {
        width: '100%',
        alignItems: 'center',
      },
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
  WithRouterProps &
  WithStyles<typeof styles> &
  WithUserProps &
  WidthProps;

type Props = OwnProps & StateProps & NotOwnProps;

class PersonDetail extends React.Component<Props, State> {
  constructor(props: Props) {
    super(props);
    let params = new URLSearchParams(qs.stringify(props.router.query));

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
        parseFilterParamsFromQs(qs.stringify(props.router.query)),
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
    const { isLoggedIn, userSelf, router } = this.props;

    if (this.state.needsFetch) {
      this.props.personFetchInitiated({ id: router.query.id as string });
    }

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
          <IconButton
            onClick={this.toggleFilters}
            className={classes.settings}
            color={this.state.showFilter ? 'primary' : 'default'}
          >
            <Tune />
            <Typography variant="srOnly">Tune</Typography>
          </IconButton>
          <CreateSmartListButton onClick={this.createListFromFilters} />
        </div>
        <div className={classes.filters}>
          <ActiveFilters
            genres={genres}
            updateFilters={this.handleFilterParamsChange}
            filters={this.state.filters}
            isListDynamic={false}
            variant="default"
          />
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
          <React.Fragment>
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
          </React.Fragment>
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

    const truncatedBio = showFullBiography
      ? biography
      : biography.substr(0, truncateSize);
    const formattedBiography = truncatedBio
      .split('\n')
      .filter(s => s.length > 0)
      .map(part => (
        <React.Fragment>
          <Typography color="inherit">{part}</Typography>
          <br />
        </React.Fragment>
      ));

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
          <Hidden smDown>{this.renderTitle(person)}</Hidden>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          <React.Fragment>{formattedBiography}</React.Fragment>
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
        {/* <Helmet>
          
        </Helmet> */}
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
                    height: '100%',
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
                    onClick={this.props.router.back}
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
                    <Hidden mdUp>{this.renderTitle(person)}</Hidden>
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
  const id = props.router.query.id as string;
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
    // isFetching: appState.itemDetail.fetching,
    person: appState.people.peopleById[id] || appState.people.peopleBySlug[id],
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
