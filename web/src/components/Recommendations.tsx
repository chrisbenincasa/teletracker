import {
  Avatar,
  createStyles,
  Grid,
  Theme,
  Typography,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import { CastMember, Thing } from '../types';
import React, { Component } from 'react';
import { getMetadataPath } from '../utils/metadata-access';
import { parseInitials } from '../utils/textHelper';
import RouterLink from './RouterLink';
import * as R from 'ramda';
import _ from 'lodash';
import { Person } from 'themoviedb-client-typed';

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
}

type Props = OwnProps & WithStyles<typeof styles>;

class Recommendations extends Component<Props, {}> {
  render() {
    const { classes, itemDetail } = this.props;
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

          <Grid container className={classes.grid}>
            {recommendations.cast.map(person => (
              <div
                className={classes.recommendationContainer}
                key={person.id}
              />
            ))}
          </Grid>
        </div>
      </React.Fragment>
    ) : null;
  }
}

export default withStyles(styles)(Recommendations);
