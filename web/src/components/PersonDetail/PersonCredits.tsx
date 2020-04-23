import { default as React, useEffect, useState } from 'react';
import { CircularProgress, Grid, Typography } from '@material-ui/core';
import ShowFiltersButton from '../Buttons/ShowFiltersButton';
import ActiveFilters from '../Filters/ActiveFilters';
import AllFilters from '../Filters/AllFilters';
import InfiniteScroll from 'react-infinite-scroller';
import _ from 'lodash';
import ItemCard from '../ItemCard';
import useStyles from './PersonDetail.styles';
import { useGenres, useNetworks } from '../../hooks/useStateMetadata';
import { Person } from '../../types/v2/Person';
import { useStateDeepEq } from '../../hooks/useStateDeepEq';
import { filterParamsEqual } from '../../utils/changeDetection';
import { DEFAULT_FILTER_PARAMS, FilterParams } from '../../utils/searchFilters';
import * as R from 'ramda';
import { parseFilterParamsFromQs } from '../../utils/urlHelper';
import qs from 'querystring';
import { useRouter } from 'next/router';
import useToggleCallback from '../../hooks/useToggleCallback';
import { useDebouncedCallback } from 'use-debounce';
import { useDispatchAction } from '../../hooks/useDispatchAction';
import { personCreditsFetchInitiated } from '../../actions/people/get_credits';
import useStateSelector, {
  useStateSelectorWithPrevious,
} from '../../hooks/useStateSelector';
import { createSelector } from 'reselect';
import { selectPerson } from './hooks';
import { Item } from '../../types/v2/Item';
import { collect } from '../../utils/collection-utils';
import { useWithUserContext } from '../../hooks/useWithUser';
import { hookDeepEqual } from '../../hooks/util';

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

interface Props {
  person: Person;
}

export default function PersonCredits(props: Props) {
  const { person } = props;

  const router = useRouter();
  const filterParams = R.mergeDeepRight(
    DEFAULT_FILTER_PARAMS,
    R.filter(R.compose(R.not, R.isNil))(
      parseFilterParamsFromQs(qs.stringify(router.query)),
    ),
  ) as FilterParams;

  const classes = useStyles();
  const [filters, setFilters] = useStateDeepEq(filterParams, filterParamsEqual);
  const genres = useGenres();
  const networks = useNetworks();
  const { userSelf } = useWithUserContext();

  const [showLoadingCredits, setShowLoadingCredits] = useState(false);
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

  const [
    { credits, creditsBookmark, loadingCredits },
    prevCreditsState,
  ] = useStateSelectorWithPrevious(state =>
    selectCreditDetails(state, person.id),
  );
  const itemById = useStateSelector(
    state => state.itemDetail.thingsById,
    hookDeepEqual,
  );

  let dispatchCreditsFetch = useDispatchAction(personCreditsFetchInitiated);

  const toggleFilters = useToggleCallback(setShowFilter);

  const handleFilterParamsChange = (filterParams: FilterParams) => {
    if (!filterParamsEqual(filters, filterParams)) {
      setFilters(filterParams);
    }
  };

  // Turn off the credits loading indicator when credits were fetched.
  useEffect(() => {
    if (Boolean(prevCreditsState?.loadingCredits) && !loadingCredits) {
      setShowLoadingCredits(false);
    }
  }, [loadingCredits]);

  // Reload credits when the filters change.
  useEffect(() => {
    setShowLoadingCredits(true);
    if (person) {
      loadCredits(person, filters, undefined);
    }
  }, [filters]);

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

  const renderLoadingCircle = () => {
    return (
      <div className={classes.loadingCircle}>
        <div>
          <CircularProgress color="secondary" />
        </div>
      </div>
    );
  };

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
        prefilledName={person.name}
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
                <ItemCard key={item.id} userSelf={userSelf} itemId={item.id} />
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
}
