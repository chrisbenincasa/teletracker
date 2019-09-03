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
  itemDetail: Thing;
  userSelf: UserSelf;
}

type Props = OwnProps & WithStyles<typeof styles>;

class Recommendations extends Component<Props, {}> {
  render() {
    const { classes, itemDetail, userSelf } = this.props;
    const recommendations = Object(
      getMetadataPath(itemDetail, 'recommendations'),
    );

    return recommendations &&
      recommendations.results &&
      recommendations.results.length > 0 ? (
      <React.Fragment>
        <div className={classes.recommendationsContainer}>
          <Typography color="inherit" variant="h5">
            Recommendations:
          </Typography>

          <Grid container spacing={2} className={classes.grid}>
            {recommendations.results.map(item => {
              return item && item.poster_path ? (
                <ItemCard
                  key={item.id}
                  userSelf={userSelf}
                  item={item}
                  itemCardVisible={false}
                />
              ) : null;
            })}
          </Grid>
        </div>
      </React.Fragment>
    ) : null;
  }
}

export default withStyles(styles)(Recommendations);
