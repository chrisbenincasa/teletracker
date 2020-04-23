import React, { useEffect } from 'react';
import {
  createStyles,
  Fade,
  Grid,
  makeStyles,
  Theme,
  Typography,
} from '@material-ui/core';
import ItemCard from './ItemCard';
import { UserSelf } from '../reducers/user';
import { Item } from '../types/v2/Item';
import { calculateLimit } from '../utils/list-utils';
import { useWidth } from '../hooks/useWidth';
import _ from 'lodash';
import useStateSelector from '../hooks/useStateSelector';
import { hookDeepEqual } from '../hooks/util';
import { createSelector } from 'reselect';
import { AppState } from '../reducers';
import { useDispatchAction } from '../hooks/useDispatchAction';
import { itemRecommendationsFetchInitiated } from '../actions/item-detail/get_item_recommendations';

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    grid: {
      justifyContent: 'flex-start',
    },
    header: {
      padding: theme.spacing(1, 0),
      fontWeight: 700,
    },
  }),
);

interface Props {
  itemDetail: Item;
  userSelf?: UserSelf;
}

const recommendationsSelector = createSelector(
  (state: AppState) => state.itemDetail.thingsById,
  (_, recIds: string[]) => recIds,
  (itemsById, recIds) => {
    return recIds.filter(
      itemId => itemsById[itemId] && itemsById[itemId].posterImage,
    );
  },
);

function Recommendations(props: Props) {
  const classes = useStyles();
  const { itemDetail, userSelf } = props;

  // Pre-filter all recs that don't include a poster
  let recommendations = useStateSelector(
    state => recommendationsSelector(state, itemDetail.recommendations || []),
    hookDeepEqual,
  );

  const fetchRecommendations = useDispatchAction(
    itemRecommendationsFetchInitiated,
  );

  const alreadyFetchedRecs = useStateSelector(
    state => state.itemDetail.currentItemFetchedRecommendations,
  );

  const fetchingRecs = useStateSelector(state => state.itemDetail.fetchingRecs);

  useEffect(() => {
    if (!alreadyFetchedRecs && !fetchingRecs) {
      fetchRecommendations({
        id: props.itemDetail.id,
        type: props.itemDetail.type,
      });
    }
  }, []);

  const width = useWidth();
  let limit = Math.min(calculateLimit(width, 2), recommendations.length);

  return (
    <Fade in={recommendations.length > 0}>
      <React.Fragment>
        <Typography color="inherit" variant="h5" className={classes.header}>
          You may also like&hellip;
        </Typography>
        <Grid container spacing={2} className={classes.grid}>
          {recommendations.slice(0, limit).map(itemId => {
            return (
              <ItemCard key={itemId} userSelf={userSelf} itemId={itemId} />
            );
          })}
        </Grid>
      </React.Fragment>
    </Fade>
  );
}

export default React.memo(Recommendations, (prevProps, newProps) => {
  return _.isEqual(prevProps, newProps);
});
