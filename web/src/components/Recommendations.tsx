import React from 'react';
import {
  createStyles,
  Grid,
  Theme,
  Typography,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import ItemCard from './ItemCard';
import { UserSelf } from '../reducers/user';
import { Item } from '../types/v2/Item';
import { calculateLimit } from '../utils/list-utils';
import { useWidth } from '../hooks/useWidth';

const styles = (theme: Theme) =>
  createStyles({
    recommendationsContainer: {
      marginTop: theme.spacing(1),
    },
    characterName: {
      fontWeight: 'bold',
    },
    grid: {
      justifyContent: 'flex-start',
    },
    recommendationContainer: {
      display: 'flex',
      flexDirection: 'column',
      maxWidth: 100,
      margin: theme.spacing(1),
    },
  });

interface OwnProps {
  itemDetail: Item;
  userSelf?: UserSelf;
}

type Props = OwnProps & WithStyles<typeof styles>;

function Recommendations(props: Props) {
  const { classes, itemDetail, userSelf } = props;
  let recommendations = itemDetail.recommendations || [];
  // Pre-filter all recs that don't include a poster
  recommendations = recommendations.filter(item => item && item.posterImage);

  const width = useWidth();
  let limit = Math.min(calculateLimit(width, 2), recommendations.length);

  return recommendations && recommendations.length > 0 ? (
    <React.Fragment>
      <div className={classes.recommendationsContainer}>
        <Typography color="inherit" variant="h5">
          Recommendations:
        </Typography>
        <Grid container spacing={2} className={classes.grid}>
          {recommendations.slice(0, limit).map(item => {
            return <ItemCard key={item.id} userSelf={userSelf} item={item} />;
          })}
        </Grid>
      </div>
    </React.Fragment>
  ) : null;
}

export default withStyles(styles)(Recommendations);
