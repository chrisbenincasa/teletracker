import {
  createStyles,
  Grid,
  Theme,
  Typography,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import { Thing } from '../types';
import React, { Component } from 'react';
import { getMetadataPath } from '../utils/metadata-access';
import _ from 'lodash';
import ItemCard from './ItemCard';
import { UserSelf } from '../reducers/user';

const styles = (theme: Theme) =>
  createStyles({
    characterName: {
      fontWeight: 'bold',
    },
    grid: { justifyContent: 'flex-start' },
    similarContainer: {
      marginTop: 10,
    },
  });

interface OwnProps {
  itemDetail: Thing;
  userSelf: UserSelf;
}

type Props = OwnProps & WithStyles<typeof styles>;

class Similar extends Component<Props, {}> {
  render() {
    const { classes, itemDetail, userSelf } = this.props;
    const similar = Object(getMetadataPath(itemDetail, 'similar'));

    return similar && similar.results && similar.results.length > 0 ? (
      <React.Fragment>
        <div className={classes.similarContainer}>
          <Typography color="inherit" variant="h5">
            Similar:
          </Typography>

          <Grid container spacing={2} className={classes.grid}>
            {similar.results.map(item => {
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

export default withStyles(styles)(Similar);
