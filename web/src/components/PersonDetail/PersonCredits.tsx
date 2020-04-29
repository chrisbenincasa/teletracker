import { default as React, useContext, useEffect, useState } from 'react';
import { CircularProgress, Grid, Typography } from '@material-ui/core';
import ShowFiltersButton from '../Buttons/ShowFiltersButton';
import ActiveFilters from '../Filters/ActiveFilters';
import AllFilters from '../Filters/AllFilters';
import InfiniteScroll from 'react-infinite-scroller';
import _ from 'lodash';
import ItemCard from '../ItemCard';
import useStyles from './PersonDetail.styles';
import { Person } from '../../types/v2/Person';
import { FilterParams } from '../../utils/searchFilters';
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
import { hookDeepEqual } from '../../hooks/util';
import { FilterContext } from '../Filters/FilterContext';
import useFilterLoadEffect from '../../hooks/useFilterLoadEffect';

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

  const classes = useStyles();
  const { filters } = useContext(FilterContext);

  const [showLoadingCredits, setShowLoadingCredits] = useState(false);
  const [showFilter, setShowFilter] = useState(
    _.some(
      [
        filters?.sortOrder,
        filters?.genresFilter,
        filters?.networks,
        filters?.itemTypes,
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

  // Turn off the credits loading indicator when credits were fetched.
  useEffect(() => {
    if (Boolean(prevCreditsState?.loadingCredits) && !loadingCredits) {
      setShowLoadingCredits(false);
    }
  }, [loadingCredits]);

  // Reload credits when the filters change.
  useFilterLoadEffect(
    () => {
      if (person) {
        setShowLoadingCredits(true);
        loadCredits(person, filters, undefined);
      }
    },
    state => state.people.detail?.currentFilters,
  );

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
        <ActiveFilters isListDynamic={false} variant="default" />
      </div>
      <AllFilters
        open={showFilter}
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
                <ItemCard key={item.id} itemId={item.id} />
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
