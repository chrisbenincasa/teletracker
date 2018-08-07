import React, { Component } from 'react';
import { View } from 'react-native';
import { Chip } from 'react-native-paper';
import getMetadata from './Helpers/getMetadata';
import { Thing } from '../Model/external/themoviedb';

import styles from './Styles/GetGenres';

interface Props {
    item?: Thing
}

export default class GetGenres extends Component<Props> {
    constructor(props: Props) {
        super(props);
    }

    render () {
        return (
            getMetadata.getGenre(this.props.item) ?
                <View style={styles.genreContainer}>
                    {
                        getMetadata.getGenre(this.props.item)
                            ? getMetadata.getGenre(this.props.item).map((i) => (
                                <Chip key={i.id}>{i.name}</Chip>
                            ))
                            : null
                    }
                </View>
            : null
        )
    }
};