import React from 'react';
import { createStyles, Fade, Grid, Theme, Typography } from '@material-ui/core';
import ItemCard from './ItemCard';
import { UserSelf } from '../reducers/user';
import { Item } from '../types/v2/Item';
import { calculateLimit } from '../utils/list-utils';
import { useWidth } from '../hooks/useWidth';
import { makeStyles } from '@material-ui/core/styles';
import _ from 'lodash';

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

function Recommendations(props: Props) {
  const classes = useStyles();
  const { itemDetail, userSelf } = props;

  // Pre-filter all recs that don't include a poster
  let recommendations = (itemDetail.recommendations || []).filter(
    item => item && item.posterImage,
  );

  const width = useWidth();
  let limit = Math.min(calculateLimit(width, 2), recommendations.length);

  return (
    <Fade in={recommendations.length > 0}>
      <React.Fragment>
        <Typography color="inherit" variant="h5" className={classes.header}>
          You may also like&hellip;
        </Typography>
        <Grid container spacing={2} className={classes.grid}>
          {recommendations.slice(0, limit).map(item => {
            return <ItemCard key={item.id} userSelf={userSelf} item={item} />;
          })}
        </Grid>
      </React.Fragment>
    </Fade>
  );
}

export default React.memo(Recommendations, (prevProps, newProps) => {
  return _.isEqual(prevProps, newProps);
});
