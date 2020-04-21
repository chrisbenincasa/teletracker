import {
  Button,
  CardMedia,
  CircularProgress,
  createStyles,
  Grid,
  Hidden,
  LinearProgress,
  makeStyles,
  Theme,
  Typography,
} from '@material-ui/core';
import { ChevronLeft, ExpandLess, ExpandMore } from '@material-ui/icons';
import _ from 'lodash';
import * as R from 'ramda';
import { default as React, useCallback, useEffect, useState } from 'react';
import InfiniteScroll from 'react-infinite-scroller';
import {
  personCreditsFetchInitiated,
  PersonCreditsFetchInitiatedPayload,
} from '../actions/people/get_credits';
import {
  personFetchInitiated,
  PersonFetchInitiatedPayload,
} from '../actions/people/get_person';
import imagePlaceholder from '../../public/images/imagePlaceholder.png';
import CreateDynamicListDialog from '../components/Dialogs/CreateDynamicListDialog';
import ActiveFilters from '../components/Filters/ActiveFilters';
import AllFilters from '../components/Filters/AllFilters';
import ShowFiltersButton from '../components/Buttons/ShowFiltersButton';
import ItemCard from '../components/ItemCard';
import ManageTrackingButton from '../components/Buttons/ManageTrackingButton';
import { ResponsiveImage } from '../components/ResponsiveImage';
import { AppState } from '../reducers';
import { Genre, Network } from '../types';
import { Item } from '../types/v2/Item';
import { Person } from '../types/v2/Person';
import { filterParamsEqual } from '../utils/changeDetection';
import { collect, extractValue } from '../utils/collection-utils';
import { DEFAULT_FILTER_PARAMS, FilterParams } from '../utils/searchFilters';
import { parseFilterParamsFromQs } from '../utils/urlHelper';
import qs from 'querystring';
import ShareButton from '../components/Buttons/ShareButton';
import { useStateDeepEq } from '../hooks/useStateDeepEq';
import { useRouter } from 'next/router';
import { useWithUserContext } from '../hooks/useWithUser';
import { useDispatchAction } from '../hooks/useDispatchAction';
import useStateSelector, {
  useStateSelectorWithPrevious,
} from '../hooks/useStateSelector';
import { createSelector } from 'reselect';
import dequal from 'dequal';
import { useGenres, useNetworks } from '../hooks/useStateMetadata';
import { useWidth } from '../hooks/useWidth';
import { useDebouncedCallback } from 'use-debounce';
import useToggleCallback from '../hooks/useToggleCallback';

const useStyles = makeStyles((theme: Theme) =>
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
    header: {
      padding: theme.spacing(1, 0),
      fontWeight: 700,
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
      marginTop: theme.spacing(1),
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
      marginBottom: theme.spacing(2),
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
      width: '100%',
      marginBottom: theme.spacing(1),
      zIndex: theme.zIndex.mobileStepper,
      [theme.breakpoints.down('sm')]: {
        textAlign: 'center',
        alignItems: 'center',
        margin: theme.spacing(1, 0, 2, 0),
      },
    },
    trackingButton: {
      marginTop: theme.spacing(1),
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
  }),
);

interface OwnProps {
  preloaded?: boolean;
}

interface State {
  showFullBiography: boolean;
  showFilter: boolean;
  filters: FilterParams;
  // Indicates that the current person in state doesn't have the necessary info to show the full detail
  // page, so we need a full fetch.
  needsFetch: boolean;
  loadingCredits: boolean;
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

// type NotOwnProps = DispatchProps &
//   WithRouterProps &
//   WithStyles<typeof styles> &
//   WithUserProps &
//   WidthProps;

// type Props = OwnProps & StateProps & NotOwnProps;

interface NewProps {
  preloaded?: boolean;
}

const selectPerson = createSelector(
  (state: AppState) => state.people.peopleById,
  (state: AppState) => state.people.detail?.current,
  (_, personId) => personId,
  (peopleById, currentPerson, personId) => {
    const person: Person | undefined =
      peopleById[personId] || extractValue(personId, undefined, peopleById);

    return person;
  },
);

const selectCreditDetails = createSelector(
  selectPerson,
  (_, personId: string) => personId,
  state => state.people.detail,
  (person, personId, personDetail) => {
    let loadedCreditsMatch = false;
    if (person) {
      loadedCreditsMatch =
        personId === person.id ||
        (person.slug ? personId === person.slug : false);
    }

    return {
      credits:
        personDetail && loadedCreditsMatch ? personDetail.credits : undefined,
      creditsBookmark:
        personDetail && loadedCreditsMatch ? personDetail.bookmark : undefined,
      loadingCredits:
        personDetail && loadedCreditsMatch ? personDetail.loading : false,
    };
  },
);

function PersonDetail(props: NewProps) {
  const classes = useStyles();
  const router = useRouter();
  const width = useWidth();
  const personId = router.query.id as string;

  let defaultFilterParams = {
    ...DEFAULT_FILTER_PARAMS,
    sortOrder: 'recent',
  };

  let filterParams = R.mergeDeepRight(
    DEFAULT_FILTER_PARAMS,
    R.filter(R.compose(R.not, R.isNil))(
      parseFilterParamsFromQs(qs.stringify(router.query)),
    ),
  ) as FilterParams;

  const [showFullBiography, setShowFullBiography] = useState(false);
  // TODO: Is this right?
  const [showFilter, setShowFilter] = useState(
    _.some(
      [
        filterParams?.sortOrder,
        filterParams?.genresFilter,
        filterParams?.networks,
        filterParams?.itemTypes,
      ],
      _.negate(_.isUndefined),
    ),
  );
  const [filters, setFilters] = useStateDeepEq(filterParams, filterParamsEqual);
  const [showLoadingCredits, setShowLoadingCredits] = useState(false);
  const [createPersonListDialogOpen, setCreatePersonListDialogOpen] = useState(
    false,
  );
  const [needsFetch, setNeedsFetch] = useState(false);
  const userState = useWithUserContext();
  const itemById = useStateSelector(
    state => state.itemDetail.thingsById,
    dequal,
  );
  const [person, prevPerson] = useStateSelectorWithPrevious(state =>
    selectPerson(state, personId),
  );
  const [loadingPerson, prevLoadingPerson] = useStateSelectorWithPrevious(
    state => state.people.loadingPeople,
  );
  const [
    { credits, creditsBookmark, loadingCredits },
    prevCreditsState,
  ] = useStateSelectorWithPrevious(state =>
    selectCreditDetails(state, personId),
  );
  const networks = useNetworks();
  const genres = useGenres();

  let dispatchPersonFetch = useDispatchAction(personFetchInitiated);
  let dispatchCreditsFetch = useDispatchAction(personCreditsFetchInitiated);

  //
  // Effects
  //

  // When the component is mounted, determine if we need a refetch of the person
  // due to missing filmography data from previous fetches.
  useEffect(() => {
    if (person) {
      let reallyNeedsFetch =
        _.isUndefined(person.cast_credit_ids) ||
        person.cast_credit_ids.data.length === 0 ||
        _.some(person.cast_credit_ids.data, creditId =>
          _.isUndefined(itemById[creditId]),
        );
      setNeedsFetch(reallyNeedsFetch);
    } else {
      setNeedsFetch(true);
    }
  }, []);

  // Re-fetch the person when the needsFetch flag is turned to true.
  useEffect(() => {
    if (needsFetch) {
      dispatchPersonFetch({
        id: router.query.id as string,
        forDetailPage: true,
      });
    }
  }, [needsFetch]);

  // Turn off needsFetch is a re-fetch completed.
  useEffect(() => {
    if ((!prevPerson && person) || (prevLoadingPerson && !loadingPerson)) {
      setNeedsFetch(false);
    }
  }, [person, loadingPerson]);

  // Turn off the credits loading indicator when credits were fetched.
  useEffect(() => {
    if (Boolean(prevCreditsState?.loadingCredits) && !loadingCredits) {
      setShowLoadingCredits(false);
    }
  }, [loadingCredits]);

  const loadCredits = (
    person: Person,
    filters: FilterParams,
    creditsBookmark: string | undefined,
  ) => {
    dispatchCreditsFetch({
      personId: person.id,
      filterParams: filters,
      limit: 18, // TODO: use calculateLimit
      bookmark: creditsBookmark,
    });
  };

  // Reload credits when the filters change.
  useEffect(() => {
    setShowLoadingCredits(true);
    if (person) {
      loadCredits(person, filters, undefined);
    }
  }, [filters]);

  // Infinite scroll callback on credits.
  const [loadMoreResults] = useDebouncedCallback(
    (
      person,
      filters,
      credits,
      creditsBookmark,
      loadingCredits,
      showLoadingCredits,
    ) => {
      if (
        (_.isUndefined(credits) || !_.isUndefined(creditsBookmark)) &&
        !loadingCredits &&
        !showLoadingCredits
      ) {
        setShowLoadingCredits(true);
        loadCredits(person, filters, creditsBookmark);
      }
    },
    100,
  );

  const toggleFilters = useToggleCallback(setShowFilter);
  const toggleShowFullBio = useToggleCallback(setShowFullBiography);

  //
  // Biz Logic
  //

  const openCreateListDialog = useCallback(() => {
    setCreatePersonListDialogOpen(true);
  }, []);

  const closeCreateListDialog = useCallback(() => {
    setCreatePersonListDialogOpen(false);
  }, []);

  const handleFilterParamsChange = (filterParams: FilterParams) => {
    if (!filterParamsEqual(filters, filterParams)) {
      setFilters(filterParams);
    }
  };

  const personFiltersForCreateDialog = useCallback(() => {
    return {
      ...DEFAULT_FILTER_PARAMS,
      people: [person!.canonical_id],
    } as FilterParams;
  }, [person]);

  //
  // Render
  //

  const renderLoadingCircle = () => {
    return (
      <div className={classes.loadingCircle}>
        <div>
          <CircularProgress color="secondary" />
        </div>
      </div>
    );
  };

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
        <Typography
          color="inherit"
          variant="h2"
          itemProp="name"
          style={{ lineHeight: 0.85 }}
        >
          {`${person.name}`}
        </Typography>
      </div>
    );
  };

  const renderFilmography = () => {
    let filmography: Item[];
    if (credits) {
      filmography = collect(credits, id => itemById[id]);
    } else {
      filmography = person!.cast_credit_ids
        ? collect(person!.cast_credit_ids.data, id => itemById[id])
        : [];
    }

    return (
      <React.Fragment>
        <div className={classes.listHeader}>
          <div className={classes.listNameContainer}>
            <Typography color="inherit" variant="h5" className={classes.header}>
              Filmography
            </Typography>
          </div>
          <ShowFiltersButton onClick={toggleFilters} />
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
          networks={networks}
          isListDynamic={false}
          prefilledName={person!.name}
          disableStarring
        />

        <InfiniteScroll
          pageStart={0}
          loadMore={() =>
            loadMoreResults(
              person,
              filters,
              credits,
              creditsBookmark,
              loadingCredits,
              showLoadingCredits,
            )
          }
          useWindow
          hasMore={_.isUndefined(credits) || !_.isUndefined(creditsBookmark)}
          threshold={300}
        >
          <React.Fragment>
            <Grid container spacing={2}>
              {filmography.map(item =>
                item && item.posterImage ? (
                  <ItemCard
                    key={item.id}
                    userSelf={userState.userSelf}
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
          </React.Fragment>
        </InfiniteScroll>
      </React.Fragment>
    );
  };

  const renderDescriptiveDetails = (person: Person) => {
    const biography = person.biography || '';
    const isMobile = ['xs', 'sm'].includes(width);
    const truncateSize = isMobile ? 300 : 1200;

    const truncatedBio = showFullBiography
      ? biography
      : biography.substr(0, truncateSize);
    const formattedBiography = truncatedBio
      .split('\n')
      .filter(s => s.length > 0)
      .map((part, index) => (
        <React.Fragment key={index}>
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
          <Hidden smDown>{renderTitle(person)}</Hidden>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          <Typography color="inherit" variant="h5" className={classes.header}>
            Biography
          </Typography>
          <React.Fragment>{formattedBiography}</React.Fragment>
          {biography.length > truncateSize ? (
            <Button
              size="small"
              variant="contained"
              aria-label={showFullBiography ? 'Read Less' : 'Read More'}
              onClick={toggleShowFullBio}
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

  const renderPerson = () => {
    if (!person || loadingPerson || needsFetch) {
      return renderLoading();
    }

    const isMobile = ['xs', 'sm'].includes(width);
    const backdrop = person?.cast_credit_ids?.data
      .map(itemId => {
        let item = itemById[itemId];
        if (item?.backdropImage) {
          return item;
        }
      })
      .find(item => !_.isUndefined(item));

    return (
      <React.Fragment>
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
                    pointerEvents: 'none', // Disables ios preview on tap & hold
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
                    onClick={router.back}
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
                    <Hidden mdUp>{renderTitle(person)}</Hidden>
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
                      <div className={classes.trackingButton}>
                        <ManageTrackingButton
                          cta={'Track Actor'}
                          onClick={openCreateListDialog}
                        />
                      </div>
                      <div className={classes.trackingButton}>
                        <ShareButton
                          title={person.name}
                          text={''}
                          url={window.location.href}
                        />
                      </div>
                    </div>
                  </div>
                  <div className={classes.personInformationContainer}>
                    {renderDescriptiveDetails(person)}
                    {renderFilmography()}
                  </div>
                </div>
              </div>
              <CreateDynamicListDialog
                filters={personFiltersForCreateDialog()}
                open={createPersonListDialogOpen}
                onClose={closeCreateListDialog}
                networks={networks || []}
                genres={genres || []}
                prefilledName={person!.name}
              />
            </React.Fragment>
          )}
        </div>
      </React.Fragment>
    );
  };

  return <div>{renderPerson()}</div>;
}

// PersonDetailF.whyDidYouRender = true;

export default PersonDetail;
