import {
  Avatar,
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
import { parseInitials } from '../utils/textHelper';

const styles = (theme: Theme) =>
  createStyles({
    avatar: {
      width: 100,
      height: 100,
    },
    actualName: {
      fontStyle: 'Italic',
    },
    castContainer: {
      marginTop: 10,
    },
    characterName: {
      fontWeight: 'bold',
    },
    grid: { justifyContent: 'flex-start' },
    personContainer: {
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

class ThingAvailability extends Component<Props, {}> {
  constructor(props: Props) {
    super(props);
  }

  render() {
    const { classes, itemDetail } = this.props;
    const credits = Object(getMetadataPath(itemDetail, 'credits'));

    return credits && credits.cast && credits.cast.length > 0 ? (
      <React.Fragment>
        <div className={classes.castContainer}>
          <Typography color="inherit" variant="h5">
            Cast
          </Typography>

          <Grid container className={classes.grid}>
            {credits.cast.map(person => (
              <div className={classes.personContainer}>
                <Avatar
                  alt={person.name}
                  src={
                    person.profile_path
                      ? `https://image.tmdb.org/t/p/w185/${person.profile_path}`
                      : ''
                  }
                  className={classes.avatar}
                >
                  {person.profile_path
                    ? null
                    : parseInitials(person.name, 'name')}
                </Avatar>
                <Typography
                  variant="subtitle1"
                  color="inherit"
                  className={classes.characterName}
                  align="center"
                >
                  {person.character}
                </Typography>
                <Typography
                  variant="subtitle2"
                  color="inherit"
                  className={classes.actualName}
                  align="center"
                >
                  {person.name}
                </Typography>
              </div>
            ))}
          </Grid>
        </div>
      </React.Fragment>
    ) : null;
  }
}

export default withStyles(styles)(ThingAvailability);
