import {
  createStyles,
  Grid,
  Theme,
  Typography,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import React, { Component } from 'react';
import { getMetadataPath } from '../utils/metadata-access';
import ItemCard from './ItemCard';
import { UserSelf } from '../reducers/user';
import Thing from '../types/Thing';
import { ApiItem } from '../types/v2';
import { Item } from '../types/v2/Item';

const styles = (theme: Theme) =>
  createStyles({
    recommendationsContainer: {
      marginTop: 10,
    },
    characterName: {
      fontWeight: 'bold',
    },
    grid: { justifyContent: 'flex-start' },
    recommendationContainer: {
      display: 'flex',
      flexDirection: 'column',
      maxWidth: 100,
      margin: 10,
    },
  });

interface OwnProps {
  itemDetail: Item;
  userSelf?: UserSelf;
}

type Props = OwnProps & WithStyles<typeof styles>;

class Recommendations extends Component<Props, {}> {
  render() {
    const { classes, itemDetail, userSelf } = this.props;
    const recommendations = itemDetail.recommendations || [];

    return recommendations && recommendations.length > 0 ? (
      <React.Fragment>
        <div className={classes.recommendationsContainer}>
          <Typography color="inherit" variant="h5">
            Recommendations:
          </Typography>

          <Grid container spacing={2} className={classes.grid}>
            {recommendations.map(item => {
              return item && item.posterImage ? (
                <ItemCard key={item.id} userSelf={userSelf} item={item} />
              ) : null;
            })}
          </Grid>
        </div>
      </React.Fragment>
    ) : null;
  }
}

export default withStyles(styles)(Recommendations);
