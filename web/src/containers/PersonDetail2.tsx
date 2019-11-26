import {
  Button,
  CardMedia,
  CircularProgress,
  Grid,
  Hidden,
  IconButton,
  LinearProgress,
  makeStyles,
  Theme,
  Typography,
} from '@material-ui/core';
import { layoutStyles } from '../styles';
import { fade } from '@material-ui/core/styles';
import { useHistory, useLocation, useRouteMatch } from 'react-router';
import useStateSelector, {
  useStateSelectorWithPrevious,
} from '../hooks/useStateSelector';
import React, { useEffect, useState } from 'react';
import _ from 'lodash';
import { Helmet } from 'react-helmet';
import { Person } from '../types/v2/Person';
import { ResponsiveImage } from '../components/ResponsiveImage';
import { ChevronLeft, ExpandLess, ExpandMore, Tune } from '@material-ui/icons';
import imagePlaceholder from '../assets/images/imagePlaceholder.png';
import ManageTrackingButton from '../components/ManageTrackingButton';
import CreateDynamicListDialog from '../components/CreateDynamicListDialog';
import { useWidth } from '../hooks/useWidth';
import { DEFAULT_FILTER_PARAMS, FilterParams } from '../utils/searchFilters';
import { filterParamsEqual } from '../utils/changeDetection';
import { useStateDeepEqWithPrevious } from '../hooks/useStateDeepEq';
import useToggle from '../hooks/useToggle';
import { Item } from '../types/v2/Item';
import { collect } from '../utils/collection-utils';
import CreateSmartListButton from '../components/Buttons/CreateSmartListButton';
import ActiveFilters from '../components/Filters/ActiveFilters';
import AllFilters from '../components/Filters/AllFilters';
import InfiniteScroll from 'react-infinite-scroller';
import ItemCard from '../components/ItemCard';
import { useDispatchAction } from '../hooks/useDispatchAction';
import { personCreditsFetchInitiated } from '../actions/people/get_credits';
import { useWithUser } from '../hooks/useWithUser';
import { usePrevious } from '../hooks/usePrevious';
import { personFetchInitiated } from '../actions/people/get_person';
import { calculateLimit } from '../utils/list-utils';

const useStyles = makeStyles((theme: Theme) => ({
  layout: layoutStyles(theme),
  backdrop: {
    width: '100%',
    height: '100%',
    display: 'flex',
    zIndex: 1,
  },
  backdropContainer: {
    height: 'auto',
    [theme.breakpoints.down('sm')]: {
      height: '100%',
    },
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
    [theme.breakpoints.up('sm')]: {
      display: 'flex',
    },
    marginBottom: theme.spacing(1),
    flexGrow: 1,
  },
  genre: { margin: 5 },
  genreContainer: {
    display: 'flex',
    flexWrap: 'wrap',
    flexDirection: 'column',
  },
  leftContainer: {
    display: 'flex',
    flexDirection: 'column',

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
      marginLeft: theme.spacing(3),
    },
    display: 'flex',
    flex: '1 1 auto',
    backgroundColor: 'transparent',
    color: '#fff',
    flexDirection: 'column',
    position: 'relative',
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
    [theme.breakpoints.up('sm')]: {
      width: 250,
    },
    margin: '0 auto',
    width: '50%',
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
    marginBottom: theme.spacing(1),
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
}));

interface OwnProps {}

interface RouteProps {
  id: string;
}

const personMissingData = (person: Person) =>
  _.isUndefined(person.cast_credits) ||
  person.cast_credits.data.length === 0 ||
  _.some(person.cast_credits.data, credit => _.isUndefined(credit.item));

export default function PersonDetail(props: OwnProps) {
  const classes = useStyles();
  const history = useHistory();
  const location = useLocation();
  const match = useRouteMatch<RouteProps>();
  const width = useWidth();

  const withUserState = useWithUser();
  const fetchPersonCredits = useDispatchAction(personCreditsFetchInitiated);
  const fetchPerson = useDispatchAction(personFetchInitiated);

  const [needsFetch, setNeedsFetch] = useState(false);
  const neededFetch = usePrevious(needsFetch);
  const [loadingCredits, setLoadingCredits] = useState(false);
  const [showFilter, toggleShowFilter] = useToggle(false);
  const [showFullBio, toggleShowFullBio] = useToggle(false);

  const [filters, setFilters, previousFilters] = useStateDeepEqWithPrevious(
    DEFAULT_FILTER_PARAMS,
    filterParamsEqual,
  );

  const [
    createDynamicListDialogOpen,
    toggleCreateDynamicListDialogOpen,
  ] = useToggle(false);

  const [
    createPersonListDialogOpen,
    toggleCreatePersonListDialogOpen,
  ] = useToggle(false);

  const networks = useStateSelector(state => state.metadata.networks);
  const genres = useStateSelector(state => state.metadata.genres);

  const [person, previousPerson] = useStateSelectorWithPrevious(
    state =>
      state.people.peopleById[match.params.id] ||
      state.people.peopleBySlug[match.params.id],
  );

  const [loadingPerson, wasLoadingPerson] = useStateSelectorWithPrevious(
    state => state.people.loadingPeople,
  );

  const itemsById = useStateSelector(state => state.itemDetail.thingsById);

  const credits = useStateSelector(state =>
    state.people.detail ? state.people.detail.credits : undefined,
  );

  const creditsBookmark = useStateSelector(state =>
    state.people.detail ? state.people.detail.bookmark : undefined,
  );

  const [
    loadingCreditsExternal,
    wasLoadingCreditsExternal,
  ] = useStateSelectorWithPrevious(state =>
    state.people.detail ? state.people.detail.loading : false,
  );

  const loadCredits = (passBookmark: boolean) => {
    setLoadingCredits(true);
    fetchPersonCredits({
      personId: person!.id,
      filterParams: filters,
      limit: calculateLimit(width, 3),
      bookmark: passBookmark ? creditsBookmark : undefined,
    });
  };

  // Load the person if we determined we need a fetch
  useEffect(() => {
    if (!neededFetch && needsFetch && !loadingPerson) {
      fetchPerson({ id: match.params.id });
    }
  }, [needsFetch, neededFetch, loadingPerson, fetchPerson, match.params.id]);

  // Load new set of filtered credits when filters change
  useEffect(() => {
    if (!filterParamsEqual(previousFilters, filters) && person) {
      loadCredits(false);
    }
  }, [filters, previousFilters, person, creditsBookmark]);

  // If we landed on the page fetch the person iff:
  //   1. We have them in local state, but they're missing info
  //   2. We don't have them in local state
  useEffect(() => {
    if (person) {
      setNeedsFetch(personMissingData(person));
    } else {
      setNeedsFetch(true);
    }
  }, []);

  // Reset needsFetch once we've retrieved the person.
  useEffect(() => {
    if ((!loadingPerson && wasLoadingPerson) || (!previousPerson && person)) {
      setNeedsFetch(false);
    }
  }, [person, loadingPerson, previousPerson, wasLoadingPerson]);

  // Reset loadingCredits once we've retrieved the current outstanding page of credits
  useEffect(() => {
    if (!loadingCreditsExternal && wasLoadingCreditsExternal) {
      setLoadingCredits(false);
    }
  }, [loadingCreditsExternal, wasLoadingCreditsExternal]);

  const creditFiltersForCreateDialog = (): FilterParams => {
    return {
      ...filters,
      people: [...(filters.people || []), person!.canonical_id],
    };
  };

  const personFiltersForCreateDialog = (): FilterParams => {
    return {
      ...DEFAULT_FILTER_PARAMS,
      people: [person!.canonical_id],
    };
  };

  const handleFilterParamsChange = (filterParams: FilterParams) => {
    setFilters(filterParams);
  };

  const loadMoreResults = () => {
    if (Boolean(credits) && !loadingCredits && !loadingCredits) {
      loadCredits(true);
    }
  };

  /*
    Render functions
   */

  const renderLoading = () => {
    return (
      <div style={{ flexGrow: 1 }}>
        <LinearProgress />
      </div>
    );
  };

  const renderTitle = (person: Person) => {
    return (
      <div className={classes.titleContainer}>
        <Typography color="inherit" variant="h4">
          {`${person.name}`}
        </Typography>
      </div>
    );
  };

  const renderLoadingCircle = () => {
    return (
      <div className={classes.loadingCircle}>
        <div>
          <CircularProgress color="secondary" />
        </div>
      </div>
    );
  };

  const renderDescriptiveDetails = (person: Person) => {
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
          <Hidden only={['xs', 'sm']}>{renderTitle(person)}</Hidden>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          <Typography color="inherit">
            {showFullBio ? biography : biography.substr(0, truncateSize)}
          </Typography>
          {biography.length > 1200 ? (
            <Button
              size="small"
              variant="contained"
              aria-label={showFullBio ? 'Read Less' : 'Read More'}
              onClick={toggleShowFullBio}
              style={{ marginTop: 5, display: 'flex', alignSelf: 'center' }}
            >
              {showFullBio ? (
                <ExpandLess style={{ marginRight: 8 }} />
              ) : (
                <ExpandMore style={{ marginRight: 8 }} />
              )}
              {showFullBio ? 'Read Less' : 'Read More'}
            </Button>
          ) : null}
        </div>
      </div>
    );
  };

  const renderFilmography = () => {
    let filmography: Item[];
    if (!_.isUndefined(credits)) {
      filmography = collect(credits, id => itemsById[id]);
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
            onClick={toggleShowFilter}
            className={classes.settings}
            color={showFilter ? 'secondary' : 'inherit'}
          >
            <Tune />
            <Typography variant="srOnly">Tune</Typography>
          </IconButton>
          <CreateSmartListButton onClick={toggleCreateDynamicListDialogOpen} />
        </div>
        <div className={classes.filters}>
          <ActiveFilters
            genres={genres}
            updateFilters={handleFilterParamsChange}
            filters={filters}
            isListDynamic={false}
            variant="default"
          />
        </div>
        <AllFilters
          genres={genres}
          open={showFilter}
          filters={filters}
          updateFilters={handleFilterParamsChange}
          disableNetworks
          isListDynamic={false}
        />

        <InfiniteScroll
          pageStart={0}
          loadMore={loadMoreResults}
          hasMore={!Boolean(credits) || Boolean(creditsBookmark)}
          useWindow
          threshold={300}
        >
          <Grid container spacing={2}>
            {filmography.map(item =>
              item && item.posterImage ? (
                <ItemCard
                  key={item.id}
                  userSelf={withUserState.userSelf}
                  item={item}
                />
              ) : null,
            )}
          </Grid>
          {loadingCredits && renderLoadingCircle()}
          {!credits ||
            (!Boolean(creditsBookmark) && (
              <Typography className={classes.fin}>fin.</Typography>
            ))}
        </InfiniteScroll>
      </div>
    );
  };

  const renderPerson = () => {
    if (!person || loadingPerson || needsFetch) {
      return renderLoading();
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
      <div>
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
            content={`https://${process.env.REACT_APP_HOST}${location.pathname}`}
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
            href={`https://${process.env.REACT_APP_HOST}${location.pathname}`}
          />
        </Helmet>
        <div className={classes.backdrop}>
          {backdrop && (
            <div>
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
                    onClick={history.goBack}
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
                    <Hidden smUp>{renderTitle(person)}</Hidden>
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
                      onClick={toggleCreatePersonListDialogOpen}
                    />
                  </div>
                  <div className={classes.personInformationContainer}>
                    {renderDescriptiveDetails(person)}
                    {renderFilmography()}
                  </div>
                </div>
              </div>
              <CreateDynamicListDialog
                filters={creditFiltersForCreateDialog()}
                open={createDynamicListDialogOpen}
                onClose={toggleCreateDynamicListDialogOpen}
                networks={networks || []}
                genres={genres || []}
              />
              <CreateDynamicListDialog
                filters={personFiltersForCreateDialog()}
                open={createPersonListDialogOpen}
                onClose={toggleCreatePersonListDialogOpen}
                networks={networks || []}
                genres={genres || []}
                prefilledName={person!.name}
              />
            </div>
          )}
        </div>
      </div>
    );
  };

  return <div style={{ display: 'flex', flexGrow: 1 }}>{renderPerson()}</div>;
}
